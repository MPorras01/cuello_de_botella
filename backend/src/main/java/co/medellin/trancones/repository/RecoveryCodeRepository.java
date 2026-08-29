package co.medellin.trancones.repository;

import co.medellin.trancones.model.RecoveryCode;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de códigos de respaldo del 2FA.
 */
public interface RecoveryCodeRepository extends ReactiveCrudRepository<RecoveryCode, Long> {

    Flux<RecoveryCode> findByUserIdAndUsedFalse(Long userId);

    Mono<Long> deleteByUserId(Long userId);
}
