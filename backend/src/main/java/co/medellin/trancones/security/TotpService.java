package co.medellin.trancones.security;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Implementación de TOTP (RFC 6238) compatible con Google Authenticator y
 * cualquier autenticador estándar. Sin dependencias externas.
 * <p>
 * Algoritmo: HMAC-SHA1, 6 dígitos, ventana de 30 s, tolerancia ±1 paso.
 * El secreto se intercambia en Base32 (formato otpauth://).
 */
@Service
public class TotpService {

    private static final int DIGITS = 6;
    private static final long TIME_STEP_SECONDS = 30;
    /** Ventana de validación: ±1 paso (permite desfases de reloj). */
    private static final int WINDOW = 1;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Genera un secreto base32 de 20 bytes (160 bits, estándar RFC 4226). */
    public String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /**
     * Construye la URI otpauth:// para mostrar como QR.
     *
     * @param issuer  nombre de la app (p. ej. "Trancones Medellín")
     * @param account identifica al usuario (email o username)
     * @param secret  secreto base32
     */
    public String otpauthUri(String issuer, String account, String secret) {
        return "otpauth://totp/" + percentEncode(issuer) + ":" + percentEncode(account)
                + "?secret=" + secret
                + "&issuer=" + percentEncode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + TIME_STEP_SECONDS;
    }

    /** Genera el código TOTP actual para un secreto base32. */
    public String generateCode(String base32Secret) {
        return generateCode(base32Secret, System.currentTimeMillis() / 1000L);
    }

    /** Verifica un código dentro de la ventana de ±1 paso. */
    public boolean verify(String base32Secret, String code) {
        if (base32Secret == null || code == null) return false;
        String expected = generateCode(base32Secret, System.currentTimeMillis() / 1000L);
        if (constantTimeEquals(expected, code)) return true;
        // tolerancia de reloj
        for (int i = 1; i <= WINDOW; i++) {
            long now = System.currentTimeMillis() / 1000L;
            if (constantTimeEquals(generateCode(base32Secret, now - i * TIME_STEP_SECONDS), code)
                    || constantTimeEquals(generateCode(base32Secret, now + i * TIME_STEP_SECONDS), code)) {
                return true;
            }
        }
        return false;
    }

    String generateCode(String base32Secret, long timeSeconds) {
        try {
            byte[] key = base32Decode(base32Secret);
            long counter = timeSeconds / TIME_STEP_SECONDS;
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error generando TOTP", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    // ─── Base32 (RFC 4648, sin padding) ─────────────────────────────────────

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32_ALPHABET.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32_ALPHABET.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return sb.toString();
    }

    static byte[] base32Decode(String encoded) {
        String clean = encoded.toUpperCase().replace("=", "");
        int buffer = 0, bits = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (char c : clean.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Secreto TOTP inválido");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private String percentEncode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
