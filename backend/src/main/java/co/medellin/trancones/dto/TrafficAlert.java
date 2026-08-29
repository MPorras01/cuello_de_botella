package co.medellin.trancones.dto;

/**
 * Alerta de tráfico, clima, calidad del aire o emergencia en el mapa.
 * Tipos: POLICE, ACCIDENT, WORKS, CLOSURE, HAZARD, WEATHER, AIR_QUALITY, FLOOD, HAZARD, OTHER.
 */
public record TrafficAlert(
    String id,
    String type,
    String title,
    String description,
    Double lat,
    Double lng,
    Integer iconCategory,
    String street,
    String fromLocation,
    String toLocation,
    // Campos extendidos para clima y calidad del aire
    String severity,
    String icon,
    java.util.Map<String, Object> metadata
) {}
