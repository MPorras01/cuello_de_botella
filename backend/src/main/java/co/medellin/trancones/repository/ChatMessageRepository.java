package co.medellin.trancones.repository;

import co.medellin.trancones.model.ChatMessage;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

/**
 * Repositorio reactivo para la tabla chat_messages.
 */
public interface ChatMessageRepository extends ReactiveCrudRepository<ChatMessage, Long> {

    Flux<ChatMessage> findByGroupIdOrderByCreatedAtAsc(Long groupId);
}
