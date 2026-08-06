package co.medellin.trancones.dto;

import java.time.Instant;

/**
 * DTO que representa el estado actual de un segmento vial.
 * Invariante: speedRatio = currentSpeed / freeFlowSpeed si freeFlowSpeed > 0, o 0.0 en caso contrario.
 * congestionLevel: "fluido" (>=0.70) | "moderado" (0.30–0.69) | "severo" (<0.30)
 * lat/lng/geometry son opcionales (null si la fuente no entrega coordenadas);
 * el frontend los usa para dibujar el segmento sobre el mapa.
 */
public record SegmentStatus(
    String segmentId,
    String segmentName,
    Double currentSpeed,
    Double freeFlowSpeed,
    Double speedRatio,
    String congestionLevel,
    Instant timestamp,
    Double lat,
    Double lng,
    GeoJsonLineString geometry
) {}
