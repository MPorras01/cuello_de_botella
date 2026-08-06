package co.medellin.trancones.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("traffic_segments")
public class TrafficSegment {

    @Id
    private Long id;

    @Column("segment_id")
    private String segmentId;

    /** Nombre del segmento / calle (alias de streetName para compatibilidad). */
    @Column("street_name")
    private String streetName;

    @Column("current_speed")
    private Double currentSpeed;

    @Column("free_flow_speed")
    private Double freeFlowSpeed;

    /** speedRatio = currentSpeed / freeFlowSpeed. < 0.30 indica cuello de botella. */
    @Column("speed_ratio")
    private Double speedRatio;

    /** Nivel derivado: "fluido" | "moderado" | "severo". No persiste en esta tabla. */
    @Transient
    private String congestionLevel;

    /** Fuente de datos: "google" | "waze" | "simm". */
    private String source;

    private Double lat;
    private Double lng;

    @Column("recorded_at")
    private LocalDateTime recordedAt;
}
