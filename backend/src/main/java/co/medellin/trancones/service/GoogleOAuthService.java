package co.medellin.trancones.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Login con Google (OAuth 2.0, flujo authorization code) sin dependencias
 * adicionales: se construye la URL de consentimiento, se intercambia el code
 * por un access token y se consulta el perfil (OpenID userinfo).
 * <p>
 * Variables: {@code GOOGLE_OAUTH_CLIENT_ID}, {@code GOOGLE_OAUTH_CLIENT_SECRET},
 * {@code GOOGLE_OAUTH_REDIRECT_URI} (callback del backend) y
 * {@code GOOGLE_OAUTH_FRONTEND_REDIRECT} (a dónde vuelve el navegador).
 */
@Slf4j
@Service
public class GoogleOAuthService {

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";

    @Value("${security.oauth.google.client-id:}")
    private String clientId;

    @Value("${security.oauth.google.client-secret:}")
    private String clientSecret;

    @Value("${security.oauth.google.redirect-uri:http://localhost:8080/api/auth/google/callback}")
    private String redirectUri;

    @Value("${security.oauth.google.frontend-redirect:http://localhost:5173}")
    private String frontendRedirect;

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    public String getFrontendRedirect() {
        return frontendRedirect;
    }

    /** URL de consentimiento de Google con el estado CSRF dado. */
    public String buildAuthUrl(String state) {
        return AUTH_URL
                + "?client_id=" + url(clientId)
                + "&redirect_uri=" + url(redirectUri)
                + "&response_type=code"
                + "&scope=" + url("openid email profile")
                + "&state=" + url(state)
                + "&prompt=select_account";
    }

    /** Intercambia el code de autorización por un access token. */
    public Mono<GoogleTokenResponse> exchangeCode(String code) {
        return WebClient.create(TOKEN_URL)
                .post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("code", code)
                        .with("client_id", clientId)
                        .with("client_secret", clientSecret)
                        .with("redirect_uri", redirectUri)
                        .with("grant_type", "authorization_code"))
                .retrieve()
                .bodyToMono(GoogleTokenResponse.class);
    }

    /** Obtiene el perfil (sub, email, name) con el access token. */
    public Mono<GoogleUserInfo> fetchUserInfo(String accessToken) {
        return WebClient.create(USERINFO_URL)
                .get()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(GoogleUserInfo.class);
    }

    private String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record GoogleTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("id_token") String idToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn
    ) {}

    public record GoogleUserInfo(
            String sub,
            String email,
            String name,
            @JsonProperty("email_verified") Boolean emailVerified
    ) {}
}
