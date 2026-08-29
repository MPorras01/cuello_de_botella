package co.medellin.trancones.repository;

import co.medellin.trancones.model.AppUser;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo para la tabla app_users.
 */
public interface UserRepository extends ReactiveCrudRepository<AppUser, Long> {

    Mono<AppUser> findByUsername(String username);

    Mono<AppUser> findByEmail(String email);

    Mono<AppUser> findByPhone(String phone);

    Mono<AppUser> findByGoogleSub(String googleSub);
}
