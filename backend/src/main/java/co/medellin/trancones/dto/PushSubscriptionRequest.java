package co.medellin.trancones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload del endpoint POST /api/push/subscribe.
 * Contiene los datos VAPID de la suscripción del navegador.
 * Todos los campos se validan para rechazar entradas malformadas.
 */
public record PushSubscriptionRequest(

        @NotBlank(message = "endpoint es obligatorio")
        @Size(max = 2048, message = "endpoint no puede exceder 2048 caracteres")
        @Pattern(regexp = "^https?://.+$", message = "endpoint debe ser una URL HTTP(S) válida")
        String endpoint,

        @NotBlank(message = "p256dh es obligatorio")
        @Size(max = 256, message = "p256dh no puede exceder 256 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9+/_-]+={0,2}$", message = "p256dh debe estar en base64url")
        String p256dh,

        @NotBlank(message = "auth es obligatorio")
        @Size(max = 128, message = "auth no puede exceder 128 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9+/_-]+={0,2}$", message = "auth debe estar en base64url")
        String auth
) {}
