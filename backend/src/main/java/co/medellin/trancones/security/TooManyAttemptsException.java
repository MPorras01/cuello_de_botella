package co.medellin.trancones.security;

/**
 * Se lanza cuando se supera el límite de intentos de inicio de sesión
 * (protección anti fuerza bruta).
 */
public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException() {
        super("Demasiados intentos de inicio de sesión");
    }
}
