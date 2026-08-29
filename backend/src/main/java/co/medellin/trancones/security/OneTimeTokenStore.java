package co.medellin.trancones.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Almacén en memoria con expiración para códigos OTP (teléfono) y tokens de
 * desafío MFA. Un solo nodo de backend: suficiente para esta aplicación; en un
 * despliegue multi-instancia debe migrarse a Redis.
 */
@Component
public class OneTimeTokenStore {

    private record Entry(String value, long expiresAtMillis) {
        boolean expired(long now) {
            return now >= expiresAtMillis;
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    /** Genera un código OTP de n dígitos y lo guarda bajo la clave dada. */
    public String putOtp(String key, int digits, long ttlSeconds) {
        String code = String.format("%0" + digits + "d", RANDOM.nextInt((int) Math.pow(10, digits)));
        entries.put(key, new Entry(code, System.currentTimeMillis() + ttlSeconds * 1000));
        return code;
    }

    /** Verifica y consume (elimina) un código OTP. */
    public boolean consumeOtp(String key, String code) {
        long now = System.currentTimeMillis();
        Entry entry = entries.get(key);
        if (entry == null || entry.expired(now)) {
            entries.remove(key);
            return false;
        }
        if (!entry.value().equals(code)) {
            return false;
        }
        entries.remove(key);
        return true;
    }

    /** Genera un token aleatorio de desafío MFA ligado al usuario. */
    public String putMfaToken(String username, long ttlSeconds) {
        String token = java.util.UUID.randomUUID().toString()
                + java.util.UUID.randomUUID().toString().replace("-", "");
        entries.put("mfa:" + token, new Entry(username, System.currentTimeMillis() + ttlSeconds * 1000));
        return token;
    }

    /** Consume el token MFA y devuelve el username ligado, o null si no es válido. */
    public String consumeMfaToken(String token) {
        long now = System.currentTimeMillis();
        Entry entry = entries.remove("mfa:" + token);
        if (entry == null || entry.expired(now)) {
            return null;
        }
        return entry.value();
    }

    /** Consume un token de estado OAuth (CSRF). Devuelve true si era válido. */
    public boolean consumeOauthState(String state) {
        long now = System.currentTimeMillis();
        Entry entry = entries.remove("oauth:" + state);
        return entry != null && !entry.expired(now);
    }

    /** Almacena un estado OAuth con expiración. */
    public void putOauthState(String state, long ttlSeconds) {
        entries.put("oauth:" + state, new Entry("state", System.currentTimeMillis() + ttlSeconds * 1000));
    }

    public void clear() {
        entries.clear();
    }
}
