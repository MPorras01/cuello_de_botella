package co.medellin.trancones.service;

import co.medellin.trancones.dto.AuthRequest;
import co.medellin.trancones.dto.AuthResponse;
import co.medellin.trancones.model.AppUser;
import co.medellin.trancones.repository.UserRepository;
import co.medellin.trancones.security.JwtService;
import co.medellin.trancones.security.LoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — Unit Tests")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private LoginRateLimiter rateLimiter;
    @Mock private GoogleOAuthService googleOAuthService;

    @InjectMocks
    private AuthService authService;

    private AppUser testUser;

    @BeforeEach
    void setUp() {
        testUser = new AppUser();
        testUser.setId(1L);
        testUser.setUsername("prueba");
        testUser.setPasswordHash("$2a$12$hashedpassword");
        testUser.setRole("USER");
        testUser.setDisplayName("Usuario Prueba");
        testUser.setTotpEnabled(false);
    }

    @Test
    @DisplayName("Login exitoso con credenciales válidas")
    void loginSuccess() {
        AuthRequest request = new AuthRequest("prueba", "prueba123");

        when(rateLimiter.check(anyString())).thenReturn(Mono.empty());
        when(userRepository.findByUsername("prueba")).thenReturn(Mono.just(testUser));
        when(passwordEncoder.matches("prueba123", testUser.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken("prueba", "USER")).thenReturn("fake-jwt-token");

        StepVerifier.create(authService.login(request, "127.0.0.1"))
                .assertNext(response -> {
                    assertThat(response.token()).isEqualTo("fake-jwt-token");
                    assertThat(response.username()).isEqualTo("prueba");
                    assertThat(response.requiresMfa()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Login fallido con contraseña incorrecta")
    void loginFailWrongPassword() {
        AuthRequest request = new AuthRequest("prueba", "wrongpassword");

        when(rateLimiter.check(anyString())).thenReturn(Mono.empty());
        when(userRepository.findByUsername("prueba")).thenReturn(Mono.just(testUser));
        when(passwordEncoder.matches("wrongpassword", testUser.getPasswordHash())).thenReturn(false);

        StepVerifier.create(authService.login(request, "127.0.0.1"))
                .expectError()
                .verify();
    }

    @Test
    @DisplayName("Login fallido con usuario inexistente")
    void loginFailUserNotFound() {
        AuthRequest request = new AuthRequest("nonexistent", "password");

        when(rateLimiter.check(anyString())).thenReturn(Mono.empty());
        when(userRepository.findByUsername("nonexistent")).thenReturn(Mono.empty());

        StepVerifier.create(authService.login(request, "127.0.0.1"))
                .expectError()
                .verify();
    }
}
