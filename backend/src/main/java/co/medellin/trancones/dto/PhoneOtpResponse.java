package co.medellin.trancones.dto;

/**
 * Respuesta de POST /api/auth/phone/request.
 * {@code devCode} solo se incluye en modo desarrollo (PHONE_SHOW_DEV_CODE=true)
 * para poder probar el flujo sin un proveedor de SMS real.
 */
public record PhoneOtpResponse(
        String message,
        String devCode
) {}
