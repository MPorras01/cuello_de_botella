package co.medellin.trancones.dto;

/**
 * DTO de respuesta del endpoint GET /api/history.
 * label: "HH:00" para rango "day", nombre del día en español para rango "week".
 */
public record HistorySummary(
    String label,
    Double avgSpeedRatio,
    String congestionLevel
) {}
