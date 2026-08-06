package co.medellin.trancones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload de POST /api/chat/groups/{id}/messages — envío de un mensaje.
 */
public record MessageRequest(

        @NotBlank(message = "content es obligatorio")
        @Size(max = 1000, message = "content no puede exceder 1000 caracteres")
        String content
) {}
