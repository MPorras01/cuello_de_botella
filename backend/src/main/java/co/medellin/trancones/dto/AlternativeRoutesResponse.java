package co.medellin.trancones.dto;

import java.util.List;

/**
 * Respuesta del endpoint GET /api/routes/alternatives.
 * Contiene hasta 2 rutas en formato GeoJSON LineString.
 */
public record AlternativeRoutesResponse(
    List<GeoJsonLineString> routes
) {}
