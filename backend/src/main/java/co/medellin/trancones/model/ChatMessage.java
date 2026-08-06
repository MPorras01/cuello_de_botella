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
 * Mensaje de un grupo de chat. Persiste en la tabla chat_messages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("chat_messages")
public class ChatMessage {

    @Id
    private Long id;

    @Column("group_id")
    private Long groupId;

    @Column("sender_username")
    private String senderUsername;

    private String content;

    @Column("created_at")
    private LocalDateTime createdAt;
}
