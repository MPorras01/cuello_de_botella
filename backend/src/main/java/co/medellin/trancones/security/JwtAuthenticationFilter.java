package co.medellin.trancones.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Filtro reactivo que extrae el token Bearer del header Authorization,
 * lo valida y puebla el SecurityContext con la autenticación del usuario.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtService jwtService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = extractBearerToken(exchange);
        if (token == null) {
            return chain.filter(exchange);
        }

        return jwtService.validate(token)
                .flatMap(claims -> {
                    String username = claims.getSubject();
                    String role = claims.get("role", String.class);
                    var authorities = List.of(new SimpleGrantedAuthority(
                            (role != null && !role.isBlank()) ? "ROLE_" + role : "ROLE_ADMIN"));
                    var authentication = new UsernamePasswordAuthenticationToken(username, null, authorities);
                    var securityContext = new SecurityContextImpl(authentication);
                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder
                                    .withSecurityContext(Mono.just(securityContext)));
                })
                .onErrorResume(InvalidTokenException.class, e -> chain.filter(exchange));
    }

    private String extractBearerToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
