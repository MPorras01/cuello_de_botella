package co.medellin.trancones.dto;

/**
 * Respuesta exitosa de POST /api/auth/login.
 */
public record AuthResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        String username
) {}
