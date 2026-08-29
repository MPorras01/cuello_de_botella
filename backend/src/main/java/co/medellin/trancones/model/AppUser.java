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
 * <p>
 * Soportes de autenticación: contraseña (email), Google OAuth ({@code googleSub}),
 * teléfono (OTP) y 2FA TOTP ({@code totpSecret}/{@code totpEnabled}).
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

    /** Nombre para mostrar (opcional). */
    @Column("display_name")
    private String displayName;

    /** Correo del usuario (único cuando está presente). */
    private String email;

    /** Teléfono con formato E.164 (único cuando está presente). */
    private String phone;

    /** Identificador sub de Google (único cuando está presente). */
    @Column("google_sub")
    private String googleSub;

    /** Secreto base32 del TOTP (presente solo mientras el 2FA está activo o en activación). */
    @Column("totp_secret")
    private String totpSecret;

    @Column("totp_enabled")
    private boolean totpEnabled;

    /** Método con el que se creó la cuenta: PASSWORD | GOOGLE | PHONE. */
    @Column("created_via")
    private String createdVia;

    @Column("created_at")
    private LocalDateTime createdAt;
}
