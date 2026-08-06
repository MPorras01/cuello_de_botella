package co.medellin.trancones.service;

import co.medellin.trancones.config.TrafficProperties;
import co.medellin.trancones.dto.*;
import co.medellin.trancones.dto.external.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Agrega tráfico de las fuentes oficiales en paralelo.
 * <ul>
 *   <li>TomTom Traffic API (free tier, flujo en tiempo real por punto)</li>
 *   <li>Google Maps Routes API (TRAFFIC_ON_POLYLINE)</li>
 *   <li>SIMM / Datos abiertos Medellín (CKAN datastore_search)</li>
 *   <li>Waze for Cities (partner feed, requiere aprobación)</li>
 * </ul>
 * Cada fuente se activa solo si su configuración está presente; si una falla,
 * se omite y se continúa con las disponibles.
 */
@Slf4j
@Service
public class TrafficAggregatorService {

    private static final double DEFAULT_FREE_FLOW = 50.0;

    private final WebClient googleWebClient;
    private final WebClient wazeWebClient;
    private final WebClient simmWebClient;
    private final WebClient tomtomWebClient;
    private final TrafficProperties props;

    public TrafficAggregatorService(
            @Qualifier("googleWebClient") WebClient googleWebClient,
            @Qualifier("wazeWebClient") WebClient wazeWebClient,
            @Qualifier("simmWebClient") WebClient simmWebClient,
            @Qualifier("tomtomWebClient") WebClient tomtomWebClient,
            TrafficProperties props) {
        this.googleWebClient = googleWebClient;
        this.wazeWebClient = wazeWebClient;
        this.simmWebClient = simmWebClient;
        this.tomtomWebClient = tomtomWebClient;
        this.props = props;
    }

    /** Resultado de un ciclo de agregación: segmentos + alertas. */
    public record TrafficAggregation(List<SegmentStatus> segments, List<TrafficAlert> alerts) {}

    /**
     * Agrega tráfico de las fuentes en paralelo (segmentos de congestión y
     * alertas de incidentes). Si una fuente falla o no está configurada,
     * se omite y se continúa con las disponibles.
     */
    public Mono<TrafficAggregation> getAggregatedTraffic() {
        long start = System.currentTimeMillis();
        return Mono.zip(
                        Flux.merge(fetchFromTomTom(), fetchFromGoogle(), fetchFromSIMM(), fetchFromWaze())
                                .collectList(),
                        fetchTomTomIncidents().collectList())
                .map(t -> new TrafficAggregation(t.getT1(), t.getT2()))
                .doOnNext(agg -> log.info("[Aggregator] Ciclo completado en {} ms — {} segmentos, {} alertas",
                        System.currentTimeMillis() - start, agg.segments().size(), agg.alerts().size()));
    }

    // ─── TomTom Traffic API (flujo en tiempo real, free tier) ──────────────

    Flux<SegmentStatus> fetchFromTomTom() {
        if (blank(props.getTomtom().getApiKey())) {
            log.warn("[Aggregator] TOMTOM_API_KEY no configurada — fuente TomTom desactivada");
            return Flux.empty();
        }
        List<TrafficProperties.Tomtom.Point> points = props.getTomtom().getPoints();
        if (points.isEmpty()) {
            log.warn("[Aggregator] Sin puntos TomTom configurados — fuente TomTom desactivada");
            return Flux.empty();
        }

        return Flux.fromIterable(points)
                .flatMap(point -> tomtomWebClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/traffic/services/4/flowSegmentData/absolute/10/json")
                                .queryParam("point", point.getLat() + "," + point.getLon())
                                .queryParam("unit", "kmph")
                                .queryParam("key", props.getTomtom().getApiKey())
                                .build())
                        .retrieve()
                        .bodyToMono(TomTomFlowResponse.class)
                        .flatMap(resp -> Mono.justOrEmpty(mapTomTomToSegment(point, resp)))
                        .onErrorResume(e -> {
                            log.warn("[Aggregator] Error en TomTom para {}: {}", point.getName(), e.getMessage());
                            return Mono.empty();
                        }), 6);
    }

    private SegmentStatus mapTomTomToSegment(TrafficProperties.Tomtom.Point point, TomTomFlowResponse resp) {
        var data = resp != null ? resp.flowSegmentData() : null;
        if (data == null || data.currentSpeed() == null || data.currentSpeed() < 0
                || data.freeFlowSpeed() == null || data.freeFlowSpeed() <= 0) {
            return null; // sin datos medibles para este punto
        }
        double ratio = calculateSpeedRatio(data.currentSpeed(), data.freeFlowSpeed());

        List<List<Double>> coords = new ArrayList<>();
        if (data.coordinates() != null && data.coordinates().coordinate() != null) {
            for (var c : data.coordinates().coordinate()) {
                if (c != null && c.latitude() != 0 && c.longitude() != 0) {
                    coords.add(List.of(c.longitude(), c.latitude()));
                }
            }
        }
        Double lat = point.getLat();
        Double lng = point.getLon();
        if (coords.size() >= 2) {
            List<Double> mid = coords.get(coords.size() / 2);
            if (mid != null && mid.size() >= 2) {
                lng = mid.get(0);
                lat = mid.get(1);
            }
        }

        return new SegmentStatus(
                "tomtom-" + slug(point.getName()),
                point.getName(),
                data.currentSpeed(), data.freeFlowSpeed(), ratio,
                congestionLevel(ratio), Instant.now(),
                lat, lng,
                coords.size() >= 2 ? GeoJsonLineString.of(coords) : null);
    }

    // ─── Google Maps Routes API ──────────────────────────────────────────────

    Flux<SegmentStatus> fetchFromGoogle() {
        if (blank(props.getGoogle().getApiKey())) {
            log.warn("[Aggregator] GOOGLE_MAPS_API_KEY no configurada — fuente Google desactivada");
            return Flux.empty();
        }
        return googleWebClient.post()
                .uri("/directions/v2:computeRoutes")
                .header("X-Goog-FieldMask",
                        "routes.legs.polyline,routes.legs.travelAdvisory.speedReadingIntervals")
                .bodyValue(buildGoogleRequestBody())
                .retrieve()
                .bodyToMono(GoogleRoutesResponse.class)
                .flatMapMany(resp -> Flux.fromIterable(mapGoogleToSegments(resp)))
                .onErrorResume(e -> {
                    log.warn("[Aggregator] Error en fuente Google: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    private String buildGoogleRequestBody() {
        // Área de Medellín — origen y destino aproximados para obtener segmentos
        return """
                {
                  "origin": {"location": {"latLng": {"latitude": 6.2442, "longitude": -75.5812}}},
                  "destination": {"location": {"latLng": {"latitude": 6.1899, "longitude": -75.5748}}},
                  "travelMode": "DRIVE",
                  "routingPreference": "TRAFFIC_AWARE",
                  "polylineEncoding": "GEOJSON_LINESTRING",
                  "extraComputations": ["TRAFFIC_ON_POLYLINE"]
                }
                """;
    }

    private List<SegmentStatus> mapGoogleToSegments(GoogleRoutesResponse resp) {
        if (resp == null || resp.routes() == null) return List.of();
        return resp.routes().stream()
                .flatMap(r -> r.legs() == null ? Stream.empty() : r.legs().stream())
                .filter(l -> l.travelAdvisory() != null
                        && l.travelAdvisory().speedReadingIntervals() != null)
                .flatMap(leg -> {
                    List<List<Double>> lineCoords =
                            (leg.polyline() != null && leg.polyline().geoJsonLinestring() != null
                                    && leg.polyline().geoJsonLinestring().coordinates() != null)
                                    ? leg.polyline().geoJsonLinestring().coordinates()
                                    : List.of();
                    return leg.travelAdvisory().speedReadingIntervals().stream()
                            .map(iv -> googleIntervalToSegment(iv, lineCoords));
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private SegmentStatus googleIntervalToSegment(
            GoogleRoutesResponse.SpeedReadingInterval interval, List<List<Double>> lineCoords) {
        double current = switch (interval.speed() == null ? "NORMAL" : interval.speed()) {
            case "NORMAL" -> 45.0;
            case "SLOW" -> 20.0;
            case "TRAFFIC_JAM" -> 5.0;
            default -> 40.0;
        };
        double ratio = calculateSpeedRatio(current, DEFAULT_FREE_FLOW);
        String segId = "google-" + interval.startPolylinePointIndex() + "-" + interval.endPolylinePointIndex();

        List<List<Double>> slice = sliceCoords(lineCoords,
                interval.startPolylinePointIndex(), interval.endPolylinePointIndex());
        Double lat = null;
        Double lng = null;
        if (slice.size() >= 2) {
            List<Double> mid = slice.get(slice.size() / 2);
            lng = mid.get(0);
            lat = mid.get(1);
        }
        return new SegmentStatus(
                segId, "Segmento Google",
                current, DEFAULT_FREE_FLOW, ratio,
                congestionLevel(ratio), Instant.now(),
                lat, lng,
                slice.size() >= 2 ? GeoJsonLineString.of(slice) : null);
    }

    /** Recorta la polilínea del leg entre dos índices de punto (con límites seguros). */
    private List<List<Double>> sliceCoords(List<List<Double>> coords, int start, int end) {
        if (coords == null || coords.isEmpty()) return List.of();
        int s = Math.max(0, Math.min(start, coords.size() - 1));
        int e = Math.max(s, Math.min(end, coords.size() - 1));
        if (e - s < 1) return List.of();
        return coords.subList(s, e + 1);
    }

    // ─── SIMM / Datos abiertos Medellín (CKAN, público) ─────────────────────

    Flux<SegmentStatus> fetchFromSIMM() {
        if (blank(props.getSimm().getResourceId())) {
            log.warn("[Aggregator] SIMM_RESOURCE_ID no configurado — fuente SIMM desactivada (ver .env.example)");
            return Flux.empty();
        }
        return simmWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/3/action/datastore_search")
                        .queryParam("resource_id", props.getSimm().getResourceId())
                        .queryParam("limit", "200")
                        .build())
                .retrieve()
                .bodyToMono(SimmSearchResponse.class)
                .flatMapMany(resp -> Flux.fromIterable(mapSimmToSegments(resp)))
                .onErrorResume(e -> {
                    log.warn("[Aggregator] Error en fuente SIMM: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    private List<SegmentStatus> mapSimmToSegments(SimmSearchResponse resp) {
        if (resp == null || resp.result() == null || resp.result().records() == null) return List.of();
        var fields = props.getSimm().getFields();
        java.util.concurrent.atomic.AtomicInteger idx = new java.util.concurrent.atomic.AtomicInteger(0);
        return resp.result().records().stream()
                .map(record -> {
                    Double current = toDouble(record.get(fields.getSpeed()));
                    Double free = toDouble(record.get(fields.getFreeFlow()));
                    if (current == null || free == null || free <= 0) return null;
                    double ratio = calculateSpeedRatio(current, free);
                    Double lat = toDouble(record.get(fields.getLat()));
                    Double lng = toDouble(record.get(fields.getLng()));
                    Object nameRaw = record.get(fields.getName());
                    String name = nameRaw != null ? String.valueOf(nameRaw) : "Sensor SIMM";
                    String id = "simm-" + idx.incrementAndGet();
                    return new SegmentStatus(
                            id, name, current, free, ratio,
                            congestionLevel(ratio), Instant.now(),
                            lat, lng, null);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ─── TomTom Traffic Incidents (alertas: policía, accidentes, obras) ─────

    /** Área del Valle de Aburrá para consultar incidentes (west,south,east,north). */
    private static final String INCIDENTS_BBOX = "-75.74,6.08,-75.36,6.44";

    /** Máximo de alertas por ciclo (evita saturar el mapa). */
    private static final int MAX_ALERTS = 50;

    /** Categorías que se muestran como alertas (se descartan ruido como niebla/hielo). */
    private static final Set<Integer> RELEVANT_CATEGORIES = Set.of(
            2, 8, 9, 10, 15, 16, 18, 19, 20, 21, 22, 23);

    /** Categorías de icono de TomTom → tipo de alerta para el frontend. */
    private static final Map<Integer, String> INCIDENT_TYPE = new LinkedHashMap<>();
    private static final Map<Integer, String> INCIDENT_NAME = new LinkedHashMap<>();

    static {
        INCIDENT_TYPE.put(2, "ACCIDENT");
        INCIDENT_TYPE.put(8, "CLOSURE");
        INCIDENT_TYPE.put(9, "CLOSURE");
        INCIDENT_TYPE.put(10, "WORKS");
        INCIDENT_TYPE.put(18, "WORKS");
        INCIDENT_TYPE.put(19, "POLICE");
        INCIDENT_TYPE.put(15, "HAZARD");
        INCIDENT_TYPE.put(16, "HAZARD");
        INCIDENT_TYPE.put(20, "HAZARD");
        INCIDENT_TYPE.put(21, "HAZARD");
        INCIDENT_TYPE.put(22, "HAZARD");
        INCIDENT_TYPE.put(23, "HAZARD");

        INCIDENT_NAME.put(1, "Incidente desconocido");
        INCIDENT_NAME.put(2, "Accidente");
        INCIDENT_NAME.put(3, "Niebla");
        INCIDENT_NAME.put(4, "Condiciones peligrosas");
        INCIDENT_NAME.put(5, "Lluvia intensa");
        INCIDENT_NAME.put(6, "Hielo en la vía");
        INCIDENT_NAME.put(7, "Trancón");
        INCIDENT_NAME.put(8, "Carril cerrado");
        INCIDENT_NAME.put(9, "Vía cerrada");
        INCIDENT_NAME.put(10, "Obras en la vía");
        INCIDENT_NAME.put(11, "Viento fuerte");
        INCIDENT_NAME.put(12, "Inundación");
        INCIDENT_NAME.put(13, "Desvío");
        INCIDENT_NAME.put(14, "Congestión");
        INCIDENT_NAME.put(15, "Vehículo averiado");
        INCIDENT_NAME.put(16, "Conducción temeraria");
        INCIDENT_NAME.put(17, "Quitanieves");
        INCIDENT_NAME.put(18, "Construcción");
        INCIDENT_NAME.put(19, "Policía");
        INCIDENT_NAME.put(20, "Vehículo incendiado");
        INCIDENT_NAME.put(21, "Bomberos");
        INCIDENT_NAME.put(22, "Cámara de velocidad");
        INCIDENT_NAME.put(23, "Obstrucción");
        INCIDENT_NAME.put(24, "Otro");
        INCIDENT_NAME.put(25, "Evento programado");
    }

    /**
     * Consulta los incidentes en tiempo real de TomTom en el área metropolitana.
     * Requiere la misma TOMTOM_API_KEY (free tier incluye incidentes).
     */
    Flux<TrafficAlert> fetchTomTomIncidents() {
        if (blank(props.getTomtom().getApiKey())) {
            return Flux.empty(); // ya se advirtió en fetchFromTomTom()
        }
        return tomtomWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        // v5 usa path limpio (sin style/zoom) y no admite fields/categoryFilter
                        .path("/traffic/services/5/incidentDetails")
                        .queryParam("key", props.getTomtom().getApiKey())
                        .queryParam("bbox", INCIDENTS_BBOX)
                        .queryParam("language", "es-ES")
                        .build())
                .retrieve()
                .bodyToMono(TomTomIncidentsResponse.class)
                .flatMapMany(resp -> Flux.fromIterable(mapTomTomIncidents(resp)))
                .onErrorResume(e -> {
                    log.warn("[Aggregator] Error en incidentes TomTom: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    private List<TrafficAlert> mapTomTomIncidents(TomTomIncidentsResponse resp) {
        if (resp == null || resp.incidents() == null) return List.of();
        // v5 no incluye id ni descripciones: se genera id desde las coordenadas,
        // se deduplican incidentes en la misma cuadra y se acota la cantidad.
        LinkedHashMap<Long, TrafficAlert> dedup = new LinkedHashMap<>();
        for (var incident : resp.incidents()) {
            if (dedup.size() >= MAX_ALERTS) break;
            var props = incident.properties();
            double[] coord = incidentCentroid(incident.geometry());
            if (props == null || coord == null) continue;
            int cat = props.iconCategory() != null ? props.iconCategory() : 1;
            if (!RELEVANT_CATEGORIES.contains(cat)) continue;

            String type = INCIDENT_TYPE.getOrDefault(cat, "OTHER");
            String title = INCIDENT_NAME.getOrDefault(cat, "Incidente");
            // Dedupe por cuadrante de ~1 km (2 decimales) para agrupar cierres
            // consecutivos del mismo corredor en un solo marcador.
            long key = Math.round(coord[0] * 100) * 10000L + Math.round(coord[1] * 100);
            dedup.putIfAbsent(key, new TrafficAlert(
                    "tomtom-inc-" + key, type, title, title,
                    coord[1], coord[0], cat));
        }
        return new ArrayList<>(dedup.values());
    }

    /**
     * Extrae un punto representativo [lon, lat] de la geometría del incidente.
     * Point → sus coordenadas; LineString → el punto medio de la polilínea.
     */
    private double[] incidentCentroid(TomTomIncidentsResponse.Geometry geometry) {
        if (geometry == null || geometry.coordinates() == null || !geometry.coordinates().isArray()) {
            return null;
        }
        JsonNode coords = geometry.coordinates();
        if (coords.isEmpty()) return null;

        // Point: [lon, lat]
        if (coords.get(0).isNumber()) {
            return new double[] { coords.get(0).asDouble(), coords.get(1).asDouble() };
        }
        // LineString: [[lon, lat], ...] → punto medio
        List<double[]> points = new ArrayList<>();
        for (JsonNode c : coords) {
            if (c.isArray() && c.size() >= 2) {
                points.add(new double[] { c.get(0).asDouble(), c.get(1).asDouble() });
            }
        }
        if (points.isEmpty()) return null;
        double[] mid = points.get(points.size() / 2);
        return new double[] { mid[0], mid[1] };
    }

    // ─── Waze for Cities API (partner feed) ─────────────────────────────────

    Flux<SegmentStatus> fetchFromWaze() {
        if (blank(props.getWaze().getBaseUrl())) {
            log.warn("[Aggregator] WAZE_API_URL no configurada — fuente Waze desactivada (requiere aprobación Waze for Cities)");
            return Flux.empty();
        }
        // La base URL es el feed completo del Waze Partner Hub (p. ej.
        // .../waze-feeds/<token>); se conserva su path y solo se agregan filtros.
        return wazeWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("top_left", "6.3500,-75.6500")
                        .queryParam("bottom_right", "6.1000,-75.5000")
                        .queryParam("types", "ROAD_CLOSED,ACCIDENT,JAM")
                        .build())
                .retrieve()
                .bodyToMono(WazeAlertsResponse.class)
                .flatMapMany(resp -> Flux.fromIterable(mapWazeToSegments(resp)))
                .onErrorResume(e -> {
                    log.warn("[Aggregator] Error en fuente Waze: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    private List<SegmentStatus> mapWazeToSegments(WazeAlertsResponse resp) {
        if (resp == null || resp.alerts() == null) return List.of();
        return resp.alerts().stream()
                .filter(a -> a.street() != null && !a.street().isBlank())
                .map(alert -> {
                    double current = alert.speed() > 0 ? alert.speed() : 2.0;
                    double ratio = calculateSpeedRatio(current, DEFAULT_FREE_FLOW);
                    Double lat = alert.location() != null ? alert.location().y() : null;
                    Double lng = alert.location() != null ? alert.location().x() : null;
                    return new SegmentStatus(
                            "waze-" + alert.uuid(),
                            alert.street(),
                            current, DEFAULT_FREE_FLOW, ratio,
                            congestionLevel(ratio), Instant.now(),
                            lat, lng, null);
                })
                .collect(Collectors.toList());
    }

    // ─── Rutas alternativas ──────────────────────────────────────────────────

    /**
     * Retorna hasta 2 rutas alternativas para el segmentId dado.
     * Requisito 5.1
     */
    public Mono<AlternativeRoutesResponse> getAlternativeRoutes(String segmentId) {
        return googleWebClient.post()
                .uri("/directions/v2:computeRoutes")
                .header("X-Goog-FieldMask", "routes.polyline.encodedPolyline,routes.legs")
                .bodyValue(buildAlternativeRoutesBody(segmentId))
                .retrieve()
                .bodyToMono(GoogleRoutesResponse.class)
                .map(resp -> {
                    if (resp == null || resp.routes() == null) {
                        return new AlternativeRoutesResponse(List.of());
                    }
                    List<GeoJsonLineString> routes = resp.routes().stream()
                            .limit(2)
                            .map(r -> GeoJsonLineString.of(List.of(
                                    // Polilínea simplificada — en producción se decodifica el encoded polyline
                                    List.of(-75.5812, 6.2442),
                                    List.of(-75.5748, 6.1899)
                            )))
                            .collect(Collectors.toList());
                    return new AlternativeRoutesResponse(routes);
                })
                .onErrorResume(e -> {
                    log.warn("[Aggregator] Error obteniendo rutas alternativas para {}: {}", segmentId, e.getMessage());
                    return Mono.just(new AlternativeRoutesResponse(List.of()));
                });
    }

    private String buildAlternativeRoutesBody(String segmentId) {
        return """
                {
                  "origin": {"location": {"latLng": {"latitude": 6.2442, "longitude": -75.5812}}},
                  "destination": {"location": {"latLng": {"latitude": 6.1899, "longitude": -75.5748}}},
                  "travelMode": "DRIVE",
                  "computeAlternativeRoutes": true
                }
                """;
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String slug(String value) {
        return value.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    /**
     * Calcula speedRatio. Si freeFlowSpeed == 0, retorna 0.0 y emite warning.
     * Requisito 1.4, 1.5
     */
    static double calculateSpeedRatio(double currentSpeed, double freeFlowSpeed) {
        if (freeFlowSpeed == 0.0) {
            log.warn("[Aggregator] freeFlowSpeed == 0, asignando speedRatio = 0.0");
            return 0.0;
        }
        return currentSpeed / freeFlowSpeed;
    }

    /**
     * Deriva el nivel de congestión desde speedRatio.
     * fluido >= 0.70, moderado [0.30, 0.70), severo < 0.30
     */
    static String congestionLevel(double speedRatio) {
        if (speedRatio >= 0.70) return "fluido";
        if (speedRatio >= 0.30) return "moderado";
        return "severo";
    }
}
