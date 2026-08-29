package co.medellin.trancones.config;

import co.medellin.trancones.security.JwtAuthenticationFilter;
import co.medellin.trancones.security.RestAuthenticationEntryPoint;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy;

/**
 * Cadena de seguridad reactiva (WebFlux):
 * <ul>
 *   <li>API stateless: sin sesiones, sin CSRF (autenticación por Bearer JWT).</li>
 *   <li>Único endpoint público: POST /api/auth/login (y preflight OPTIONS).</li>
 *   <li>Headers de seguridad (CSP, HSTS, X-Frame-Options, etc.).</li>
 *   <li>Filtro JWT en la posición AUTHENTICATION.</li>
 * </ul>
 */
@Slf4j
@Configuration
public class SecurityConfig {

    private static final String CSP_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: blob: https:",
            "connect-src 'self' https: wss:",
            "font-src 'self' data:",
            "frame-ancestors 'none'",
            "base-uri 'self'",
            "form-action 'self'");

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint entryPoint,
            CorsConfigurationSource corsConfigurationSource) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/google/url",
                                "/api/auth/google/callback",
                                "/api/auth/phone/request",
                                "/api/auth/phone/verify",
                                "/api/auth/2fa/verify",
                                "/api/health").permitAll()
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().permitAll())
                .exceptionHandling(spec -> spec.authenticationEntryPoint(entryPoint))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP_POLICY))
                        // X-Frame-Options DENY y X-Content-Type-Options nosniff
                        // ya vienen activados por defecto en Spring Security.
                        .hsts(hsts -> hsts
                                .includeSubdomains(true)
                                .maxAge(Duration.ofDays(365)))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicy(permissions -> permissions.policy(
                                "camera=(), microphone=(), geolocation=(), payment=(), usb=()")))
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Clave HMAC-SHA256 para firmar JWT.
     * Si JWT_SECRET no está configurado o es menor a 32 bytes, se genera una
     * clave aleatoria en memoria (los tokens no sobreviven reinicios).
     */
    @Bean
    public SecretKey jwtSecretKey(@Value("${security.jwt.secret:}") String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            log.warn("[Security] JWT_SECRET no configurado: se genera una clave aleatoria (los tokens no sobreviven reinicios).");
            return Jwts.SIG.HS256.key().build();
        }
        if (configuredSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            log.warn("[Security] JWT_SECRET menor a 32 bytes: se usa una clave aleatoria. Configure una clave fuerte en producción.");
            return Jwts.SIG.HS256.key().build();
        }
        return Keys.hmacShaKeyFor(configuredSecret.getBytes(StandardCharsets.UTF_8));
    }
}
