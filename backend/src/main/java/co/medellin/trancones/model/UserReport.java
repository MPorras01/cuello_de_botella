package co.medellin.trancones.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Informe colocado por un usuario sobre el mapa (policía, accidente, obras...).
 * Persiste en la tabla user_reports.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_reports")
public class UserReport {

    @Id
    private Long id;

    private String username;

    /** POLICE | ACCIDENT | WORKS | CLOSURE | HAZARD | OTHER */
    private String type;

    private String description;

    private Double lat;

    private Double lng;

    @Column("created_at")
    private LocalDateTime createdAt;
}
