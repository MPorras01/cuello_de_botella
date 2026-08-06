package co.medellin.trancones.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Genera y valida tokens JWT firmados con HMAC-SHA256.
 * La clave se inyecta desde {@code security.jwt.secret} (JWT_SECRET).
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(SecretKey key,
                      @Value("${security.jwt.expiration-minutes:15}") long expirationMinutes) {
        this.key = key;
        this.expirationMinutes = expirationMinutes;
    }

    /**
     * Genera un token JWT para el usuario dado.
     *
     * @param username nombre de usuario (subject)
     * @param role     rol del usuario (se incluye como claim "role")
     */
    public String generateToken(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /**
     * Valida la firma y expiración de un token.
     *
     * @return Mono con los claims si el token es válido, o error {@link InvalidTokenException}
     */
    public Mono<Claims> validate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Mono.just(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Mono.error(new InvalidTokenException("Token inválido o expirado"));
        }
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }
}
