package co.medellin.trancones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload de POST /api/chat/groups — creación de un grupo de chat.
 */
public record GroupRequest(

        @NotBlank(message = "name es obligatorio")
        @Size(max = 60, message = "name no puede exceder 60 caracteres")
        String name,

        @Size(max = 300, message = "description no puede exceder 300 caracteres")
        String description
) {}
