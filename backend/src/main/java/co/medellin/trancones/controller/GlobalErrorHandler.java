package co.medellin.trancones.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

import java.util.Map;

/**
 * Manejador global de errores.
 * Nunca filtra detalles internos, trazas ni mensajes de excepción al cliente:
 * responde mensajes genéricos y controlados.
 */
@Slf4j
@RestControllerAdvice
public class GlobalErrorHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        String message = switch (status) {
            case 400 -> "Solicitud inválida";
            case 401 -> "Credenciales inválidas";
            case 404 -> "Recurso no encontrado";
            case 429 -> ex.getReason() != null ? ex.getReason() : "Demasiadas solicitudes";
            default -> "Error interno del servidor";
        };
        if (status >= 500) {
            log.error("[Error] {}: {}", ex.getStatusCode(), ex.getMessage(), ex);
        }
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    @ExceptionHandler({WebExchangeBindException.class, HandlerMethodValidationException.class})
    public ResponseEntity<Map<String, String>> handleValidation(Exception ex) {
        log.warn("[Validation] {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", "Solicitud inválida"));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<Map<String, String>> handleBadInput(ServerWebInputException ex) {
        log.warn("[BadRequest] {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", "Cuerpo de solicitud inválido"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAll(Exception ex) {
        log.error("[Error] Excepción no controlada: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error interno del servidor"));
    }
}
