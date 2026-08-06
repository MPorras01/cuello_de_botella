package co.medellin.trancones.security;

/**
 * Se lanza cuando un token JWT es inválido, malformado o expirado.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
