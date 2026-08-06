package co.medellin.trancones.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload de POST /api/reports — informe de usuario colocado en el mapa.
 * Todos los campos se validan para rechazar entradas malformadas.
 */
public record ReportRequest(

        @NotBlank(message = "type es obligatorio")
        @Pattern(regexp = "POLICE|ACCIDENT|WORKS|CLOSURE|HAZARD|OTHER",
                message = "type debe ser POLICE, ACCIDENT, WORKS, CLOSURE, HAZARD u OTHER")
        String type,

        @Size(max = 500, message = "description no puede exceder 500 caracteres")
        String description,

        @DecimalMin(value = "-90", message = "lat fuera de rango")
        @DecimalMax(value = "90", message = "lat fuera de rango")
        double lat,

        @DecimalMin(value = "-180", message = "lng fuera de rango")
        @DecimalMax(value = "180", message = "lng fuera de rango")
        double lng
) {}
