package co.medellin.trancones.service;

import co.medellin.trancones.model.AppUser;
import co.medellin.trancones.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Crea el usuario administrador al arrancar.
 * <ul>
 *   <li>Si {@code ADMIN_PASSWORD} está configurada, se crea/actualiza el hash
 *       para que coincida siempre con la contraseña del entorno.</li>
 *   <li>Si no, se genera una aleatoria SOLO la primera vez (cuando el usuario
 *       no existe) y se imprime en el log. Los reinicios posteriores no cambian
 *       la contraseña almacenada.</li>
 * </ul>
 * La contraseña siempre se persiste como hash BCrypt (nunca en claro).
 *
 * <p>NOTA: los casos "usuario existe" deben completar con un valor NO vacío;
 * {@code switchIfEmpty} solo debe dispararse ante un usuario realmente ausente
 * (un {@code Mono.empty()} en el flatMap activaría el INSERT por error).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${security.admin-username:admin}")
    private String username;

    @Value("${security.admin-password:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        boolean configured = password != null && !password.isBlank();

        Mono<Void> upsert = userRepository.findByUsername(username)
                .flatMap(existing -> {
                    if (!configured) {
                        // Contraseña generada: no tocar la ya almacenada.
                        log.info("[Security] Usuario '{}' ya existe (contraseña sin cambios)", username);
                        return Mono.just(existing);
                    }
                    if (passwordEncoder.matches(password, existing.getPasswordHash())) {
                        log.info("[Security] Usuario '{}' listo (contraseña sin cambios)", username);
                        return Mono.just(existing);
                    }
                    existing.setPasswordHash(passwordEncoder.encode(password));
                    return userRepository.save(existing);
                })
                // Solo se alcanza si el usuario NO existe en la BD.
                .switchIfEmpty(Mono.defer(() -> {
                    String effectivePassword = configured ? password : generateRandomPassword();
                    AppUser user = AppUser.builder()
                            .username(username)
                            .passwordHash(passwordEncoder.encode(effectivePassword))
                            .role("ADMIN")
                            .createdAt(LocalDateTime.now())
                            .build();
                    if (!configured) {
                        log.warn("[Security] ADMIN_PASSWORD no configurado. Usuario '{}' creado con contraseña temporal: {}",
                                username, effectivePassword);
                    }
                    return userRepository.save(user);
                }))
                .then()
                .doOnError(e -> log.error("[Security] No se pudo inicializar el usuario '{}': {}",
                        username, e.getMessage()))
                .onErrorResume(e -> Mono.empty());

        upsert.block(Duration.ofSeconds(30));
    }

    private String generateRandomPassword() {
        byte[] bytes = new byte[18];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
