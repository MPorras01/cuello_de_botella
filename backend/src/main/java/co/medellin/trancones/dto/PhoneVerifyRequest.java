package co.medellin.trancones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload del endpoint POST /api/auth/phone/verify — valida el código OTP.
 */
public record PhoneVerifyRequest(

        @NotBlank(message = "El teléfono es obligatorio")
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "El teléfono no es válido (formato E.164)")
        @Size(max = 30, message = "El teléfono no puede exceder 30 caracteres")
        String phone,

        @NotBlank(message = "El código es obligatorio")
        @Pattern(regexp = "^[0-9]{6}$", message = "El código debe tener 6 dígitos")
        String code
) {}
