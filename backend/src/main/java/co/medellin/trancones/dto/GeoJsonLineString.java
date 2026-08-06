package co.medellin.trancones.dto;

import java.util.List;

/**
 * Representación GeoJSON de tipo LineString.
 * coordinates: lista de pares [lng, lat].
 */
public record GeoJsonLineString(
    String type,
    List<List<Double>> coordinates
) {
    public static GeoJsonLineString of(List<List<Double>> coordinates) {
        return new GeoJsonLineString("LineString", coordinates);
    }
}
