package co.medellin.trancones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload para confirmar/desactivar el 2FA con un código del autenticador.
 */
public record CodeRequest(

        @NotBlank(message = "El código es obligatorio")
        @Size(max = 20, message = "El código no puede exceder 20 caracteres")
        String code
) {}
