package co.medellin.trancones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload del endpoint POST /api/auth/2fa/verify — completa el segundo factor
 * usando el token de desafío MFA entregado en el primer paso del login.
 */
public record MfaVerifyRequest(

        @NotBlank(message = "El token de desafío es obligatorio")
        @Size(max = 200, message = "Token de desafío inválido")
        String mfaToken,

        @NotBlank(message = "El código es obligatorio")
        @Size(max = 20, message = "El código no puede exceder 20 caracteres")
        String code
) {}
