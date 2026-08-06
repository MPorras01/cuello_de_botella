package co.medellin.trancones.controller;

import co.medellin.trancones.dto.AuthRequest;
import co.medellin.trancones.dto.AuthResponse;
import co.medellin.trancones.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Endpoint público de autenticación. Único endpoint de /api/** sin token.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Si está detrás de un proxy de confianza, usa X-Forwarded-For para el
     * rate-limit; en caso contrario, usa la IP de la conexión directa.
     */
    @Value("${security.login.trust-forwarded-for:false}")
    private boolean trustForwardedFor;

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<AuthResponse> login(@Valid @RequestBody AuthRequest request,
                                    ServerWebExchange exchange) {
        return authService.login(request, clientIp(exchange));
    }

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
}
