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
 * Grupo de chat público. Persiste en la tabla chat_groups.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("chat_groups")
public class ChatGroup {

    @Id
    private Long id;

    private String name;

    private String description;

    @Column("created_by")
    private String createdBy;

    @Column("created_at")
    private LocalDateTime createdAt;
}
