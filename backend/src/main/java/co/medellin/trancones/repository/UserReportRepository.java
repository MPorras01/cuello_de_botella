package co.medellin.trancones.repository;

import co.medellin.trancones.model.UserReport;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo para la tabla user_reports.
 */
public interface UserReportRepository extends ReactiveCrudRepository<UserReport, Long> {

    Flux<UserReport> findAllByOrderByCreatedAtDesc();

    Flux<UserReport> findByUsernameOrderByCreatedAtDesc(String username);

    Mono<Long> countByUsername(String username);
}
