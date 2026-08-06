package co.medellin.trancones.repository;

import co.medellin.trancones.model.PushSubscription;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo para la tabla push_subscriptions.
 * Todas las operaciones son no bloqueantes.
 */
public interface PushSubscriptionRepository extends ReactiveCrudRepository<PushSubscription, Long> {

    Mono<PushSubscription> findByEndpoint(String endpoint);

    Mono<Void> deleteByEndpoint(String endpoint);
}
