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
 * Código de respaldo del 2FA. Se almacena SOLO como hash BCrypt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_recovery_codes")
public class RecoveryCode {

    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("code_hash")
    private String codeHash;

    private boolean used;

    @Column("created_at")
    private LocalDateTime createdAt;
}
