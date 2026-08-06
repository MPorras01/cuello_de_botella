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
 * Entidad R2DBC que representa un usuario de la aplicación.
 * Persiste en la tabla app_users. La contraseña se guarda SOLO como hash BCrypt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app_users")
public class AppUser {

    @Id
    private Long id;

    private String username;

    @Column("password_hash")
    private String passwordHash;

    private String role;

    @Column("created_at")
    private LocalDateTime createdAt;
}
