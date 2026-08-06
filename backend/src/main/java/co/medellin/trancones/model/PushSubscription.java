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
 * Entidad R2DBC que representa una suscripción Web Push VAPID.
 * Persiste en la tabla push_subscriptions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("push_subscriptions")
public class PushSubscription {

    @Id
    private Long id;

    private String endpoint;
    private String p256dh;
    private String auth;

    @Column("created_at")
    private LocalDateTime createdAt;
}
