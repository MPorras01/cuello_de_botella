package co.medellin.trancones.dto.external;

import java.util.List;
import java.util.Map;

/**
 * Respuesta CKAN {@code datastore_search} del portal de datos abiertos de Medellín:
 * <pre>{ "result": { "records": [ { "campo": valor, ... }, ... ] } }</pre>
 * Los nombres de campo se leen de forma configurable en TrafficProperties
 * (el dataset del SIMM puede variar sus columnas).
 */
public record SimmSearchResponse(SimmResult result) {

    public record SimmResult(List<Map<String, Object>> records) {}
}
