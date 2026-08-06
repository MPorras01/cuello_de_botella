package co.medellin.trancones.service;

import co.medellin.trancones.dto.AuthRequest;
import co.medellin.trancones.dto.AuthResponse;
import co.medellin.trancones.model.AppUser;
import co.medellin.trancones.repository.UserRepository;
import co.medellin.trancones.security.JwtService;
import co.medellin.trancones.security.LoginRateLimiter;
import co.medellin.trancones.security.TooManyAttemptsException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Autentica credenciales contra la tabla app_users y emite tokens JWT.
 * El mensaje de error es idéntico para usuario inexistente o contraseña
 * incorrecta (evita enumeración de usuarios) y el tiempo de verificación es
 * constante (hash dummy cuando el usuario no existe).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * Hash BCrypt de una cadena aleatoria. Se verifica contra este hash cuando
     * el usuario no existe para igualar el tiempo de respuesta.
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter rateLimiter;

    public Mono<AuthResponse> login(AuthRequest request, String ip) {
        return rateLimiter.check(ip)
                .then(userRepository.findByUsername(request.username())
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
                            String token = jwtService.generateToken(user.getUsername(), user.getRole());
                            return Mono.just(new AuthResponse(
                                    token,
                                    "Bearer",
                                    jwtService.getExpirationMinutes() * 60,
                                    user.getUsername()));
                        }))
                .onErrorMap(TooManyAttemptsException.class,
                        e -> new ResponseStatusException(
                                HttpStatus.TOO_MANY_REQUESTS,
                                "Demasiados intentos de inicio de sesión. Intente más tarde."));
    }
}
