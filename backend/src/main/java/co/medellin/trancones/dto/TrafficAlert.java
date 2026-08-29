package co.medellin.trancones.dto;

/**
 * Alerta de tráfico en un punto del mapa (policía, accidente, obras, cierre, etc.).
 * {@code type} es uno de: POLICE, ACCIDENT, WORKS, CLOSURE, HAZARD, OTHER.
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
    String toLocation
) {}
