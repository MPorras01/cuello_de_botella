package co.medellin.trancones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload del endpoint POST /api/auth/login.
 */
public record AuthRequest(

        @NotBlank(message = "El usuario es obligatorio")
        @Size(max = 50, message = "El usuario no puede exceder 50 caracteres")
        String username,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(max = 100, message = "La contraseña no puede exceder 100 caracteres")
        String password
) {}
