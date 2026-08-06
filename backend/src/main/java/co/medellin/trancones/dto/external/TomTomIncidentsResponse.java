package co.medellin.trancones.dto.external;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * DTO de parseo de TomTom Traffic Incidents API (incidentDetails).
 * Las geometrías pueden ser Point [lon, lat] o LineString [[lon, lat], ...],
 * por eso coordinates se modela como JsonNode y se extrae en el servicio.
 */
public record TomTomIncidentsResponse(List<Incident> incidents) {

    public record Incident(String type, Geometry geometry, Properties properties) {}

    public record Geometry(String type, JsonNode coordinates) {}

    public record Properties(
        String id,
        Integer iconCategory,
        Integer magnitudeOfDelay,
        List<Event> events,
        String from,
        String to,
        Integer length,
        Integer delaySeconds,
        List<String> roadNumbers
    ) {}

    public record Event(String description) {}
}
