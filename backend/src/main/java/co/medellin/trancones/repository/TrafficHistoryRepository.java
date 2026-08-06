package co.medellin.trancones.repository;

import co.medellin.trancones.model.TrafficHistory;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;

/**
 * Repositorio reactivo para la tabla traffic_history.
 * Todas las operaciones son no bloqueantes.
 */
public interface TrafficHistoryRepository extends ReactiveCrudRepository<TrafficHistory, Long> {

    @Query("SELECT * FROM traffic_history WHERE ts >= :from ORDER BY ts ASC")
    Flux<TrafficHistory> findByTimestampAfter(OffsetDateTime from);
}
