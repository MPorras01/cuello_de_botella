package co.medellin.trancones.security;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Responde HTTP 401 con JSON cuando una petición protegida no está autenticada.
 * Evita redirecciones de login por defecto (API stateless).
 */
@Component
public class RestAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private static final byte[] BODY =
            "{\"error\":\"No autorizado. Inicie sesión para continuar.\"}"
                    .getBytes(StandardCharsets.UTF_8);

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(BODY);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
