package co.medellin.trancones.dto;

/**
 * Respuesta de POST /api/auth/2fa/setup — secreto y URI para el autenticador.
 */
public record TotpSetupResponse(
        String secret,
        String otpauthUrl
) {}
