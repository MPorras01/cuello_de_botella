package co.medellin.trancones.dto;

/**
 * Respuesta de GET /api/auth/2fa/status.
 */
public record TotpStatusResponse(
        boolean enabled
) {}
