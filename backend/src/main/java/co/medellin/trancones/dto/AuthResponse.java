package co.medellin.trancones.dto;

/**
 * Respuesta exitosa de POST /api/auth/login (y variantes: registro, teléfono).
 * <p>
 * Cuando el usuario tiene 2FA activo, {@code token} llega vacío y se entregan
 * {@code requiresMfa=true} + {@code mfaToken}: el cliente debe llamar a
 * POST /api/auth/2fa/verify con el código del autenticador para obtener el JWT.
 */
public record AuthResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        String username,
        String displayName,
        boolean requiresMfa,
        String mfaToken
) {

    /** Respuesta final con JWT (sin desafío MFA). */
    public static AuthResponse authenticated(String token, long expiresInSeconds,
                                             String username, String displayName) {
        return new AuthResponse(token, "Bearer", expiresInSeconds, username, displayName, false, null);
    }

    /** Desafío de segundo factor: aún no hay JWT. */
    public static AuthResponse mfaChallenge(String mfaToken, String username, String displayName) {
        return new AuthResponse(null, "Bearer", 0, username, displayName, true, mfaToken);
    }
}
