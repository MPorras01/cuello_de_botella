package co.medellin.trancones.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/**
 * Entidad R2DBC que representa un snapshot histórico de un segmento vial.
 * Persiste en la tabla traffic_history cada 5 minutos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("traffic_history")
public class TrafficHistory {

    @Id
    private Long id;

    @Column("segment_id")
    private String segmentId;

    @Column("segment_name")
    private String segmentName;

    @Column("speed_ratio")
    private Double speedRatio;

    @Column("congestion_level")
    private String congestionLevel;

    /** Timestamp en UTC. */
    @Column("ts")
    private OffsetDateTime timestamp;
}
