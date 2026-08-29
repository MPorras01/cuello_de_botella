package co.medellin.trancones.service;

import co.medellin.trancones.dto.*;
import co.medellin.trancones.model.AppUser;
import co.medellin.trancones.model.RecoveryCode;
import co.medellin.trancones.repository.RecoveryCodeRepository;
import co.medellin.trancones.repository.UserRepository;
import co.medellin.trancones.security.JwtService;
import co.medellin.trancones.security.LoginRateLimiter;
import co.medellin.trancones.security.OneTimeTokenStore;
import co.medellin.trancones.security.TooManyAttemptsException;
import co.medellin.trancones.security.TotpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Autenticación completa: contraseña (usuario o correo), registro con correo,
 * OTP por teléfono, login con Google (OAuth2) y 2FA TOTP + códigos de respaldo.
 * <p>
 * El mensaje de error es idéntico para usuario inexistente o contraseña
 * incorrecta (evita enumeración de usuarios) y la verificación es de tiempo
 * constante (hash dummy cuando el usuario no existe).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * Hash BCrypt de una cadena aleatoria. Se verifica contra este hash cuando
     * el usuario no existe para igualar el tiempo de respuesta.
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private static final int OTP_TTL_SECONDS = 300;
    private static final int MFA_TTL_SECONDS = 300;
    private static final int OTP_MAX_ATTEMPTS = 3;
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter rateLimiter;
    private final OneTimeTokenStore tokenStore;
    private final TotpService totpService;
    private final GoogleOAuthService googleOAuthService;

    /** URL opcional de un proveedor de SMS (Webhook/API) para entregar el OTP. */
    @Value("${phone.sms-api-url:}")
    private String smsApiUrl;

    /** En desarrollo devuelve el código OTP en la respuesta para poder probar. */
    @Value("${phone.show-dev-code:true}")
    private boolean showDevCode;

    // ─── Login con usuario o correo + contraseña ────────────────────────────

    public Mono<AuthResponse> login(AuthRequest request, String ip) {
        return rateLimiter.check(ip)
                .then(findByUsernameOrEmail(request.username())
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.empty())
                        .flatMap(userOpt -> {
                            AppUser user = userOpt.orElse(null);
                            String storedHash = (user != null) ? user.getPasswordHash() : DUMMY_HASH;
                            boolean matches = passwordEncoder.matches(request.password(), storedHash);
                            if (user == null || !matches) {
                                return Mono.error(new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED, "Credenciales inválidas"));
                            }
                            return issueAuthResponse(user);
                        }))
                .onErrorMap(TooManyAttemptsException.class,
                        e -> new ResponseStatusException(
                                HttpStatus.TOO_MANY_REQUESTS,
                                "Demasiados intentos de inicio de sesión. Intente más tarde."));
    }

    private Mono<AppUser> findByUsernameOrEmail(String login) {
        if (login.contains("@")) {
            return userRepository.findByEmail(login.trim().toLowerCase());
        }
        return userRepository.findByUsername(login);
    }

    // ─── Registro con correo y contraseña ───────────────────────────────────

    public Mono<AuthResponse> register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        return userRepository.findByEmail(email)
                .hasElement()
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException("El correo ya está registrado"));
                    }
                    AppUser user = AppUser.builder()
                            .username(email)
                            .displayName(request.displayName().trim())
                            .email(email)
                            .passwordHash(passwordEncoder.encode(request.password()))
                            .role("USER")
                            .createdVia("PASSWORD")
                            .totpEnabled(false)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return userRepository.save(user)
                            .flatMap(this::issueAuthResponse)
                            .onErrorResume(DataIntegrityViolationException.class,
                                    e -> Mono.error(new IllegalArgumentException(
                                            "El correo ya está registrado")));
                });
    }

    // ─── Login por teléfono (OTP) ───────────────────────────────────────────

    /** Solicita un código OTP al teléfono (en dev se devuelve en la respuesta). */
    public Mono<PhoneOtpResponse> requestPhoneOtp(PhoneRequest request) {
        String phone = normalizePhone(request.phone());
        return rateLimiter.check("otp:" + phone)
                .then(Mono.fromCallable(() ->
                        tokenStore.putOtp("otp:" + phone, 6, OTP_TTL_SECONDS)))
                .flatMap(code -> deliverOtp(phone, code))
                .map(code -> new PhoneOtpResponse(
                        "Código enviado a " + phone + " (válido por 5 minutos)",
                        showDevCode ? code : null));
    }

    /** Valida el código y hace login (crea la cuenta la primera vez). */
    public Mono<AuthResponse> verifyPhone(PhoneVerifyRequest request) {
        String phone = normalizePhone(request.phone());
        if (!tokenStore.consumeOtp("otp:" + phone, request.code())) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Código inválido o expirado"));
        }
        return userRepository.findByPhone(phone)
                .flux()
                .collectList()
                .flatMap(list -> {
                    if (!list.isEmpty()) {
                        return Mono.just(list.get(0));
                    }
                    AppUser user = AppUser.builder()
                            .username(phone)
                            .displayName(phone)
                            .phone(phone)
                            .passwordHash("") // sin contraseña: solo entra por OTP
                            .role("USER")
                            .createdVia("PHONE")
                            .totpEnabled(false)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return userRepository.save(user);
                })
                .flatMap(this::issueAuthResponse);
    }

    private Mono<String> deliverOtp(String phone, String code) {
        if (smsApiUrl == null || smsApiUrl.isBlank()) {
            log.info("[Auth] [DEV] Código OTP para {}: {}", phone, code);
            return Mono.just(code);
        }
        return WebClient.create().post()
                .uri(smsApiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "phone", phone,
                        "code", code,
                        "message", "Tu código de Trancones Medellín es " + code))
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> {
                    log.warn("[Auth] No se pudo enviar el SMS a {}: {}", phone, e.getMessage());
                    return Mono.just(code);
                })
                .thenReturn(code);
    }

    // ─── Login con Google (OAuth2) ──────────────────────────────────────────

    /**
     * Callback de Google: valida el state, intercambia el code, localiza o crea
     * la cuenta y emite la respuesta (JWT o desafío MFA si tiene 2FA).
     */
    public Mono<AuthResponse> googleCallback(String code, String state) {
        if (!tokenStore.consumeOauthState(state)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Estado de autenticación inválido o expirado"));
        }
        if (!googleOAuthService.isConfigured()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "El login con Google no está configurado"));
        }
        return googleOAuthService.exchangeCode(code)
                .flatMap(tokenResp -> googleOAuthService.fetchUserInfo(tokenResp.accessToken()))
                .flatMap(info -> {
                    String email = info.email() != null ? info.email().trim().toLowerCase() : null;
                    if (email == null || email.isBlank()) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Google no devolvió un correo válido"));
                    }
                    return userRepository.findByGoogleSub(info.sub())
                            .switchIfEmpty(userRepository.findByEmail(email))
                            .flux()
                            .collectList()
                            .flatMap(list -> {
                                if (!list.isEmpty()) {
                                    AppUser user = list.get(0);
                                    if (user.getGoogleSub() == null) {
                                        user.setGoogleSub(info.sub());
                                        return userRepository.save(user);
                                    }
                                    return Mono.just(user);
                                }
                                AppUser user = AppUser.builder()
                                        .username(email)
                                        .displayName(info.name() != null ? info.name() : email)
                                        .email(email)
                                        .googleSub(info.sub())
                                        .passwordHash("") // sin contraseña: solo entra por Google
                                        .role("USER")
                                        .createdVia("GOOGLE")
                                        .totpEnabled(false)
                                        .createdAt(LocalDateTime.now())
                                        .build();
                                return userRepository.save(user);
                            });
                })
                .flatMap(this::issueAuthResponse);
    }

    // ─── 2FA TOTP ───────────────────────────────────────────────────────────

    /** Estado del 2FA del usuario autenticado. */
    public Mono<TotpStatusResponse> totpStatus() {
        return currentUsername()
                .flatMap(this::loadUser)
                .map(user -> new TotpStatusResponse(user.isTotpEnabled()));
    }

    /** Genera el secreto TOTP y la URI otpauth para el autenticador. */
    public Mono<TotpSetupResponse> setupTotp() {
        return currentUsername()
                .flatMap(this::loadUser)
                .flatMap(user -> {
                    if (user.isTotpEnabled()) {
                        return Mono.error(new IllegalArgumentException("El 2FA ya está activo"));
                    }
                    String secret = totpService.generateSecret();
                    user.setTotpSecret(secret);
                    return userRepository.save(user)
                            .map(saved -> new TotpSetupResponse(secret,
                                    totpService.otpauthUri("Trancones Medellín",
                                            accountOf(saved), secret)));
                });
    }

    /** Confirma un código del autenticador y activa el 2FA + códigos de respaldo. */
    public Mono<TotpConfirmResponse> confirmTotp(CodeRequest request) {
        return currentUsername()
                .flatMap(this::loadUser)
                .flatMap(user -> {
                    if (user.isTotpEnabled()) {
                        return Mono.error(new IllegalArgumentException("El 2FA ya está activo"));
                    }
                    if (user.getTotpSecret() == null) {
                        return Mono.error(new IllegalArgumentException(
                                "Primero solicita el setup del 2FA"));
                    }
                    if (!totpService.verify(user.getTotpSecret(), request.code())) {
                        return Mono.error(new IllegalArgumentException("Código inválido"));
                    }
                    // Generar códigos ANTES de habilitar: si algo falla, el 2FA
                    // no queda activado sin sus códigos de respaldo.
                    List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
                    for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
                        codes.add(randomRecoveryCode());
                    }
                    List<RecoveryCode> entities = codes.stream().map(code -> RecoveryCode.builder()
                            .userId(user.getId())
                            .codeHash(passwordEncoder.encode(code))
                            .used(false)
                            .createdAt(LocalDateTime.now())
                            .build()).toList();
                    user.setTotpEnabled(true);
                    return recoveryCodeRepository.saveAll(Flux.fromIterable(entities))
                            .collectList()
                            .then(userRepository.save(user))
                            .then(Mono.fromSupplier(() -> new TotpConfirmResponse(codes)));
                });
    }

    /** Desactiva el 2FA (requiere un código válido del autenticador). */
    public Mono<Void> disableTotp(CodeRequest request) {
        return currentUsername()
                .flatMap(this::loadUser)
                .flatMap(user -> {
                    if (!user.isTotpEnabled()) {
                        return Mono.error(new IllegalArgumentException("El 2FA no está activo"));
                    }
                    if (!totpService.verify(user.getTotpSecret(), request.code())) {
                        return Mono.error(new IllegalArgumentException("Código inválido"));
                    }
                    user.setTotpEnabled(false);
                    user.setTotpSecret(null);
                    return userRepository.save(user)
                            .then(recoveryCodeRepository.deleteByUserId(user.getId()).then());
                });
    }

    /** Completa el segundo factor tras el primer paso del login. */
    public Mono<AuthResponse> verifyMfa(MfaVerifyRequest request) {
        String username = tokenStore.consumeMfaToken(request.mfaToken());
        if (username == null) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Desafío MFA expirado o inválido"));
        }
        return loadUser(username)
                .flatMap(user -> {
                    if (!user.isTotpEnabled()) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "El usuario no tiene el 2FA activo"));
                    }
                    if (totpService.verify(user.getTotpSecret(), request.code())) {
                        return Mono.just(tokenFor(user));
                    }
                    return verifyRecoveryCode(user, request.code())
                            .flatMap(ok -> ok
                                    ? Mono.just(tokenFor(user))
                                    : Mono.error(new ResponseStatusException(
                                            HttpStatus.UNAUTHORIZED, "Código inválido")));
                });
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    /** Usuario autenticado vía JWT (SecurityContext reactivo). */
    private Mono<String> currentUsername() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName());
    }

    private Mono<AppUser> loadUser(String username) {
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuario no encontrado")));
    }

    /** Emite el JWT o el desafío MFA según el estado del 2FA del usuario. */
    private Mono<AuthResponse> issueAuthResponse(AppUser user) {
        if (user.isTotpEnabled()) {
            String mfaToken = tokenStore.putMfaToken(user.getUsername(), MFA_TTL_SECONDS);
            return Mono.just(AuthResponse.mfaChallenge(mfaToken, user.getUsername(),
                    displayNameOf(user)));
        }
        return Mono.just(tokenFor(user));
    }

    private AuthResponse tokenFor(AppUser user) {
        return AuthResponse.authenticated(
                jwtService.generateToken(user.getUsername(), user.getRole()),
                jwtService.getExpirationMinutes() * 60,
                user.getUsername(),
                displayNameOf(user));
    }

    private String displayNameOf(AppUser user) {
        return user.getDisplayName() != null && !user.getDisplayName().isBlank()
                ? user.getDisplayName()
                : user.getUsername();
    }

    private String accountOf(AppUser user) {
        return user.getEmail() != null ? user.getEmail() : user.getUsername();
    }

    private Mono<Boolean> verifyRecoveryCode(AppUser user, String code) {
        return recoveryCodeRepository.findByUserIdAndUsedFalse(user.getId())
                .filter(rc -> passwordEncoder.matches(code, rc.getCodeHash()))
                .next()
                .flatMap(rc -> {
                    rc.setUsed(true);
                    return recoveryCodeRepository.save(rc).thenReturn(true);
                })
                .defaultIfEmpty(false);
    }

    /** Código de respaldo legible de 8 caracteres (sin 0/1/I/O ambiguos). */
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private String randomRecoveryCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
        }
        return sb.toString();
    }

    private String normalizePhone(String phone) {
        String clean = phone == null ? "" : phone.replaceAll("[\\s-]", "");
        return clean.startsWith("+") ? clean : "+" + clean;
    }
}
