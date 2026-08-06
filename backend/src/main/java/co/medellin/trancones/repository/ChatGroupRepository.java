package co.medellin.trancones.repository;

import co.medellin.trancones.model.ChatGroup;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo para la tabla chat_groups.
 */
public interface ChatGroupRepository extends ReactiveCrudRepository<ChatGroup, Long> {

    Flux<ChatGroup> findAllByOrderByCreatedAtAsc();

    Mono<ChatGroup> findByName(String name);
}
