package co.medellin.trancones.dto.external;

import java.util.List;

/**
 * DTO de parseo de respuesta de Google Maps Routes API.
 * Con {@code polylineEncoding: GEOJSON_LINESTRING} cada leg trae su
 * polilínea en coordenadas; los speedReadingIntervals referencian índices
 * sobre esa polilínea.
 */
public record GoogleRoutesResponse(List<GoogleRoute> routes) {

    public record GoogleRoute(List<GoogleLeg> legs) {}

    public record GoogleLeg(GooglePolyline polyline, GoogleTravelAdvisory travelAdvisory) {}

    public record GooglePolyline(GeoJsonLinestring geoJsonLinestring) {}

    public record GeoJsonLinestring(List<List<Double>> coordinates) {}

    public record GoogleTravelAdvisory(
        List<SpeedReadingInterval> speedReadingIntervals
    ) {}

    public record SpeedReadingInterval(
        int startPolylinePointIndex,
        int endPolylinePointIndex,
        String speed  // "NORMAL" | "SLOW" | "TRAFFIC_JAM"
    ) {}
}
