package co.medellin.trancones.controller;

import co.medellin.trancones.dto.*;
import co.medellin.trancones.security.OneTimeTokenStore;
import co.medellin.trancones.service.AuthService;
import co.medellin.trancones.service.GoogleOAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoint público de autenticación. Expone todos los métodos de acceso:
 * contraseña, registro, Google OAuth, teléfono (OTP) y 2FA.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GoogleOAuthService googleOAuthService;
    private final OneTimeTokenStore tokenStore;

    /**
     * Si está detrás de un proxy de confianza, usa X-Forwarded-For para el
     * rate-limit; en caso contrario, usa la IP de la conexión directa.
     */
    @Value("${security.login.trust-forwarded-for:false}")
    private boolean trustForwardedFor;

    // ─── Contraseña ─────────────────────────────────────────────────────────

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<AuthResponse> login(@Valid @RequestBody AuthRequest request,
                                    ServerWebExchange exchange) {
        return authService.login(request, clientIp(exchange));
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    // ─── Google OAuth ───────────────────────────────────────────────────────

    /**
     * Devuelve la URL de consentimiento de Google. El navegador debe navegar
     * a ella; al terminar, Google redirige a /api/auth/google/callback.
     */
    @GetMapping("/google/url")
    public Mono<Map<String, String>> googleUrl() {
        if (!googleOAuthService.isConfigured()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "El login con Google no está configurado (GOOGLE_OAUTH_CLIENT_ID)"));
        }
        String state = UUID.randomUUID().toString();
        tokenStore.putOauthState(state, 600);
        return Mono.just(Map.of("url", googleOAuthService.buildAuthUrl(state)));
    }

    /**
     * Callback de Google. Intercambia el code, crea/localiza la cuenta y
     * redirige al frontend con el JWT (o el desafío MFA) en el fragmento #.
     */
    @GetMapping("/google/callback")
    public Mono<ResponseEntity<Void>> googleCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state) {
        return authService.googleCallback(code, state)
                .map(resp -> {
                    String frontend = googleOAuthService.getFrontendRedirect();
                    if (resp.requiresMfa()) {
                        return redirect(frontend + "#mfa=1&mfaToken=" + resp.mfaToken()
                                + "&username=" + enc(resp.username()));
                    }
                    return redirect(frontend + "#token=" + resp.token()
                            + "&username=" + enc(resp.username())
                            + "&displayName=" + enc(resp.displayName()));
                })
                .onErrorResume(ResponseStatusException.class, e -> Mono.just(
                        redirect(googleOAuthService.getFrontendRedirect()
                                + "#error=" + enc(safeMessage(e)))));
    }

    /** Redirect 302 a la URL dada con tipo explícito (evita inferencia genérica). */
    private ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();
    }

    // ─── Teléfono (OTP) ─────────────────────────────────────────────────────

    @PostMapping(value = "/phone/request", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<PhoneOtpResponse> phoneRequest(@Valid @RequestBody PhoneRequest request) {
        return authService.requestPhoneOtp(request);
    }

    @PostMapping(value = "/phone/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<AuthResponse> phoneVerify(@Valid @RequestBody PhoneVerifyRequest request) {
        return authService.verifyPhone(request);
    }

    // ─── 2FA (TOTP) ─────────────────────────────────────────────────────────

    @GetMapping("/2fa/status")
    public Mono<TotpStatusResponse> totpStatus() {
        return authService.totpStatus();
    }

    @PostMapping("/2fa/setup")
    public Mono<TotpSetupResponse> totpSetup() {
        return authService.setupTotp();
    }

    @PostMapping(value = "/2fa/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<TotpConfirmResponse> totpConfirm(@Valid @RequestBody CodeRequest request) {
        return authService.confirmTotp(request);
    }

    @PostMapping(value = "/2fa/disable", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Void> totpDisable(@Valid @RequestBody CodeRequest request) {
        return authService.disableTotp(request);
    }

    /** Público: completa el segundo factor del login (token de desafío + código). */
    @PostMapping(value = "/2fa/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<AuthResponse> mfaVerify(@Valid @RequestBody MfaVerifyRequest request) {
        return authService.verifyMfa(request);
    }

    // ─── Utilidades ─────────────────────────────────────────────────────────

    private String clientIp(ServerWebExchange exchange) {
        if (trustForwardedFor) {
            String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        var remote = exchange.getRequest().getRemoteAddress();
        return remote != null ? remote.getAddress().getHostAddress() : "unknown";
    }

    private String enc(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value,
                java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Mensaje controlado para el redirect de error (nunca detalles internos). */
    private String safeMessage(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        return status == HttpStatus.SERVICE_UNAVAILABLE
                ? "El login con Google no está configurado"
                : "No se pudo completar el inicio de sesión con Google";
    }
}
