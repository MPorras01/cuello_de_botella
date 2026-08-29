package co.medellin.trancones.dto;

import java.util.List;

/**
 * Respuesta de POST /api/auth/2fa/confirm — 2FA activado. Incluye los códigos
 * de respaldo (se muestran UNA sola vez; guárdalos en un lugar seguro).
 */
public record TotpConfirmResponse(
        List<String> recoveryCodes
) {}
