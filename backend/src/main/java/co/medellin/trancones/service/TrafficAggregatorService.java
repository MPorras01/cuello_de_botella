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

    private final WeatherService weatherService;
    private final AirQualityService airQualityService;

    public TrafficAggregatorService(
            @Qualifier("googleWebClient") WebClient googleWebClient,
            @Qualifier("wazeWebClient") WebClient wazeWebClient,
            @Qualifier("simmWebClient") WebClient simmWebClient,
            @Qualifier("tomtomWebClient") WebClient tomtomWebClient,
            TrafficProperties props,
            WeatherService weatherService,
            AirQualityService airQualityService) {
        this.googleWebClient = googleWebClient;
        this.wazeWebClient = wazeWebClient;
        this.simmWebClient = simmWebClient;
        this.tomtomWebClient = tomtomWebClient;
        this.props = props;
        this.weatherService = weatherService;
        this.airQualityService = airQualityService;
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
        Mono<List<SegmentStatus>> segmentsMono = Flux.merge(
                        fetchFromTomTom(), fetchFromGoogle(), fetchFromSIMM(), fetchFromWaze())
                .collectList();
        Mono<List<TrafficAlert>> incidentsMono = fetchTomTomIncidents().collectList();
        Mono<List<TrafficAlert>> weatherMono = fetchWeatherAlerts().collectList()
                .onErrorResume(e -> { log.warn("[Weather] zip fallback: {}", e.getMessage()); return Mono.just(List.of()); });
        Mono<List<TrafficAlert>> airMono = fetchAirQualityAlerts().collectList()
                .onErrorResume(e -> { log.warn("[AirQuality] zip fallback: {}", e.getMessage()); return Mono.just(List.of()); });

        return Mono.zip(segmentsMono, incidentsMono, weatherMono, airMono)
                .map(t -> {
                    List<TrafficAlert> allAlerts = new ArrayList<>(t.getT2());
                    allAlerts.addAll(t.getT3());
                    allAlerts.addAll(t.getT4());
                    return new TrafficAggregation(t.getT1(), allAlerts);
                })
                .doOnNext(agg -> log.info("[Aggregator] Ciclo completado en {} ms — {} segmentos, {} alertas (weather+aq incluidos)",
                        System.currentTimeMillis() - start, agg.segments().size(), agg.alerts().size()));
    }

    // ─── Weather (Open-Meteo) ─────────────────────────────────────────────

    /**
     * Obtiene alertas de clima: lluvia fuerte, tormentas, viento peligroso.
     */
    private Flux<TrafficAlert> fetchWeatherAlerts() {
        log.info("[Weather] Consultando Open-Meteo...");
        return weatherService.getCurrentWeather()
                .doOnNext(w -> log.info("[Weather] OK: {}°C, {}, severe={}", w.temperature(), w.description(), w.severe()))
                .doOnError(e -> log.warn("[Weather] Error: {}", e.getMessage()))
                .flatMapMany(weather -> {
                    if (weather == null) { log.info("[Weather] Sin datos"); return Flux.empty(); }
                    List<TrafficAlert> alerts = new ArrayList<>();

                    // Lluvia fuerte
                    if (weather.precipitation() > 5.0) {
                        alerts.add(new TrafficAlert(
                                "weather-rain", "WEATHER",
                                "🌧️ Lluvia fuerte en Medellín",
                                String.format("Precipitación: %.1f mm/h. Temperatura: %.1°C. Viento: %.0f km/h",
                                        weather.precipitation(), weather.temperature(), weather.windSpeed()),
                                6.2476, -75.5658, null, null, null, null,
                                "MODERATE", "🌧️",
                                Map.of("temperature", weather.temperature(),
                                        "humidity", weather.humidity(),
                                        "windSpeed", weather.windSpeed(),
                                        "precipitation", weather.precipitation())));
                    }

                    // Tormenta eléctrica
                    if (weather.weatherCode() >= 95) {
                        alerts.add(new TrafficAlert(
                                "weather-storm", "WEATHER",
                                "⛈️ Tormenta eléctrica",
                                weather.description() + ". Precaución al conducir.",
                                6.2476, -75.5658, null, null, null, null,
                                "SEVERE", "⛈️",
                                Map.of("weatherCode", weather.weatherCode(),
                                        "description", weather.description())));
                    }

                    // Viento peligroso
                    if (weather.windGusts() > 50.0) {
                        alerts.add(new TrafficAlert(
                                "weather-wind", "WEATHER",
                                "💨 Ráfagas de viento fuerte",
                                String.format("Ráfagas de %.0f km/h. Viento direction: %d°",
                                        weather.windGusts(), weather.windDirection()),
                                6.2476, -75.5658, null, null, null, null,
                                "HIGH", "💨",
                                Map.of("windGusts", weather.windGusts(),
                                        "windSpeed", weather.windSpeed())));
                    }

                    return Flux.fromIterable(alerts);
                })
                .onErrorResume(e -> {
                    log.debug("[Weather] Error generando alertas: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    // ─── Air Quality (AQICN/WAQI) ────────────────────────────────────────

    /**
     * Obtiene alertas de calidad del aire de las estaciones de Medellín.
     */
    private Flux<TrafficAlert> fetchAirQualityAlerts() {
        log.info("[AirQuality] Consultando WAQI...");
        return airQualityService.getAllStations()
                .doOnNext(aq -> log.info("[AirQuality] OK: {} AQI={}", aq.stationName(), aq.aqi()))
                .doOnError(e -> log.warn("[AirQuality] Error: {}", e.getMessage()))
                .filter(aq -> aq != null)
                .map(aq -> new TrafficAlert(
                        "aqi-" + aq.stationId(), "AIR_QUALITY",
                        "🏭 Calidad del aire: " + aq.level(),
                        String.format("AQI: %d — %s. PM2.5: %s, PM10: %s",
                                aq.aqi(), aq.level(),
                                aq.pm25() != null ? String.format("%.0f", aq.pm25()) : "N/A",
                                aq.pm10() != null ? String.format("%.0f", aq.pm10()) : "N/A"),
                        6.2476, -75.5658, null, null, null, null,
                        aq.unhealthy() ? "HIGH" : "LOW", "🏭",
                        Map.of("aqi", aq.aqi(),
                                "level", aq.level(),
                                "color", aq.color(),
                                "pm25", aq.pm25() != null ? aq.pm25() : 0,
                                "pm10", aq.pm10() != null ? aq.pm10() : 0,
                                "recommendation", aq.recommendation(),
                                "station", aq.stationName())))
                .onErrorResume(e -> {
                    log.debug("[AirQuality] Error generando alertas: {}", e.getMessage());
                    return Flux.empty();
                });
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
     * Después de obtener los incidentes, resuelve las coordenadas a direcciones
     * usando reverse geocoding para obtener nombre de calle y referencias.
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
                .flatMapMany(resp -> {
                    List<TrafficAlert> alerts = mapTomTomIncidents(resp);
                    log.info("[Aggregator] {} alertas TomTom, enriqueciendo con geocoding...", alerts.size());
                    // Enriquecer con reverse geocoding (batch de las primeras 20)
                    return enrichAlertsWithGeocoding(alerts);
                })
                .onErrorResume(e -> {
                    log.warn("[Aggregator] Error en incidentes TomTom: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    /**
     * WebClient temporal para Nominatim (OpenStreetMap) — reverse geocoding gratuito.
     */
    private WebClient nominatimClient() {
        return WebClient.builder()
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader("User-Agent", "TranconesMedellin/1.0 (trancones-app)")
                .build();
    }

    /**
     * Caché en memoria para resultados de reverse geocoding.
     * Key = lat/lng redondeados a 3 decimales (~110m), Value = datos enriquecidos.
     * Evita llamadas repetidas a Nominatim para incidentes en la misma zona.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, GeocodeCacheEntry> geocodeCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    private record GeocodeCacheEntry(String street, String fromLocation, String toLocation, String description) {}

    private String geocodeKey(Double lat, Double lng) {
        // Redondear a 3 decimales (~110m) para agrupar incidentes cercanos
        return String.format("%.3f,%.3f", Math.round(lat * 1000) / 1000.0, Math.round(lng * 1000) / 1000.0);
    }

    /**
     * Enriquece las alertas con nombre de calle usando Nominatim (OSM).
     * Primero consulta la caché; solo llama a Nominatim para coordenadas
     * nuevas. Procesa hasta 15 por ciclo (1.1s entre llamadas = ~16.5s total).
     */
    private Flux<TrafficAlert> enrichAlertsWithGeocoding(List<TrafficAlert> alerts) {
        WebClient nomClient = nominatimClient();
        List<TrafficAlert> enriched = new ArrayList<>();
        List<TrafficAlert> needGeocode = new ArrayList<>();

        // Separar: las que ya tienen caché vs las que necesitan geocoding
        for (TrafficAlert alert : alerts) {
            if (alert.lat() == null || alert.lng() == null) {
                enriched.add(alert);
                continue;
            }
            if (alert.street() != null && !alert.street().isBlank()) {
                enriched.add(alert); // ya tiene calle de TomTom
                continue;
            }
            String key = geocodeKey(alert.lat(), alert.lng());
            GeocodeCacheEntry cached = geocodeCache.get(key);
            if (cached != null) {
                // Aplicar datos de caché
                enriched.add(new TrafficAlert(
                        alert.id(), alert.type(), alert.title(), cached.description(),
                        alert.lat(), alert.lng(), alert.iconCategory(),
                        cached.street(), cached.fromLocation(), cached.toLocation(),
                        alert.severity(), alert.icon(), alert.metadata()));
            } else {
                needGeocode.add(alert);
            }
        }

        // Geocodificar las que faltan (máx 15 por ciclo)
        int toGeocode = Math.min(needGeocode.size(), 15);
        List<TrafficAlert> toProcess = new ArrayList<>(needGeocode.subList(0, toGeocode));
        List<TrafficAlert> remaining = needGeocode.size() > toGeocode
                ? new ArrayList<>(needGeocode.subList(toGeocode, needGeocode.size()))
                : new ArrayList<>();
        enriched.addAll(remaining); // las que no alcanzaron van sin geocoding este ciclo

        log.info("[Geocode] {}/{} alertas desde caché, {} por geocodificar, {} sin datos",
                enriched.size() - remaining.size(), alerts.size(), toGeocode, remaining.size());

        if (toProcess.isEmpty()) {
            return Flux.fromIterable(enriched);
        }

        final int finalToGeocode = toGeocode;
        return Flux.fromIterable(toProcess)
                .concatMap(alert -> {
                    String key = geocodeKey(alert.lat(), alert.lng());
                    return nomClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/reverse")
                                    .queryParam("format", "json")
                                    .queryParam("lat", alert.lat())
                                    .queryParam("lon", alert.lng())
                                    .queryParam("zoom", "18")
                                    .queryParam("addressdetails", "1")
                                    .queryParam("accept-language", "es")
                                    .build())
                            .retrieve()
                            .bodyToMono(NominatimResponse.class)
                            .map(geoResp -> {
                                TrafficAlert enriched2 = enrichAlertFromNominatim(alert, geoResp);
                                // Guardar en caché
                                if (enriched2.street() != null) {
                                    geocodeCache.put(key, new GeocodeCacheEntry(
                                            enriched2.street(), enriched2.fromLocation(),
                                            enriched2.toLocation(), enriched2.description()));
                                }
                                return enriched2;
                            })
                            .onErrorResume(e -> {
                                log.warn("[Aggregator] Geocode falló para {}: {}", alert.id(), e.getMessage());
                                return Mono.just(alert);
                            })
                            .delayElement(java.time.Duration.ofMillis(1100));
                })
                .concatWith(Flux.fromIterable(enriched));
    }

    /**
     * Enriquece una alerta con datos del reverse geocoding de Nominatim.
     * Extrae el nombre de la calle, barrio y municipio para crear
     * una descripción rica con la ubicación textual del incidente.
     */
    private TrafficAlert enrichAlertFromNominatim(TrafficAlert alert, NominatimResponse geoResp) {
        if (geoResp == null || geoResp.address() == null) return alert;
        var addr = geoResp.address();

        // Nombre de la calle principal
        String streetName = addr.road();
        if (streetName == null || streetName.isBlank()) streetName = addr.pedestrian();
        if (streetName == null || streetName.isBlank()) streetName = addr.highway();
        String municipality = addr.city() != null ? addr.city() : addr.town();
        String neighbourhood = addr.suburb() != null ? addr.suburb() : addr.neighbourhood();

        // Construir descripción rica con calle y referencias
        String fromLoc = null;
        String toLoc = null;
        if (streetName != null && !streetName.isBlank()) {
            fromLoc = streetName;
            if (neighbourhood != null && !neighbourhood.isBlank()) {
                toLoc = neighbourhood;
            } else if (municipality != null && !municipality.isBlank()) {
                toLoc = municipality;
            }
        }

        String desc = alert.title();
        if (fromLoc != null) {
            desc = fromLoc;
            if (toLoc != null) desc += " · " + toLoc;
        }

        return new TrafficAlert(
                alert.id(), alert.type(), alert.title(), desc,
                alert.lat(), alert.lng(), alert.iconCategory(),
                streetName, fromLoc, toLoc,
                alert.severity(), alert.icon(), alert.metadata());
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
            // Construir nombre de la calle y referencias desde TomTom
            String from = props.from();
            String to = props.to();
            List<String> roads = props.roadNumbers();
            String streetName = (roads != null && !roads.isEmpty()) ? roads.get(0) : null;
            if (streetName == null && from != null && !from.isBlank()) streetName = from;
            String desc = buildAlertDescription(type, title, from, to);
            // Dedupe por cuadrante de ~1 km (2 decimales) para agrupar cierres
            // consecutivos del mismo corredor en un solo marcador.
            long key = Math.round(coord[0] * 100) * 10000L + Math.round(coord[1] * 100);
            dedup.putIfAbsent(key, new TrafficAlert(
                    "tomtom-inc-" + key, type, title, desc,
                    coord[1], coord[0], cat, streetName, from, to,
                    null, null, null));
        }
        return new ArrayList<>(dedup.values());
    }

    /**
     * Construye una descripción rica con nombre de la calle y referencias
     * del lugar exacto donde inicia y termina el incidente.
     */
    private String buildAlertDescription(String type, String title, String from, String to) {
        StringBuilder sb = new StringBuilder();
        if (from != null && !from.isBlank()) {
            sb.append(from);
            if (to != null && !to.isBlank()) {
                sb.append(" → ").append(to);
            }
        } else if (to != null && !to.isBlank()) {
            sb.append("Hasta ").append(to);
        } else {
            sb.append(title);
        }
        return sb.toString();
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
