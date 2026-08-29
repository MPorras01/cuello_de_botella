package co.medellin.trancones.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Servicio de calidad del aire usando WAQI (World Air Quality Index).
 * Estaciones en Medellín:
 *   - @556204: Itagüí (Sur)
 *   - @556210: Tanque Miraflores (Centro)
 *   - @556211: Carvajal (Sur)
 *   - @556212: Buenos Aires (Centro)
 *   - @556213: Castilla (Norte)
 *   - @556214: Centro de Datos (Centro)
 *   - @556215: Popular (Norte)
 *   - @556216: Aranjuez (Sur)
 *   - @556217: Caldas (Sur-Oeste)
 * API: https://api.waqi.info/feed/{station}/?token=TOKEN
 * Token gratuito: https://aqicn.org/data-platform/token/
 */
@Slf4j
@Service
public class AirQualityService {

    private static final String BASE_URL = "https://api.waqi.info";

    @Value("${airquality.token:demo}")
    private String token;

    private final WebClient webClient = WebClient.builder()
            .baseUrl(BASE_URL)
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();

    /**
     * Estaciones de calidad del aire en Medellín con sus IDs de WAQI.
     */
    public record StationInfo(String id, String name, String zone) {}

    private static final List<StationInfo> MEDELLIN_STATIONS = List.of(
            new StationInfo("@556210", "Tanque Miraflores", "Centro"),
            new StationInfo("@556211", "Carvajal", "Sur"),
            new StationInfo("@556212", "Buenos Aires", "Centro"),
            new StationInfo("@556213", "Castilla", "Norte"),
            new StationInfo("@556214", "Centro de Datos", "Centro"),
            new StationInfo("@556215", "Popular", "Norte"),
            new StationInfo("@556216", "Aranjuez", "Sur"),
            new StationInfo("@556204", "Itagüí", "Sur-Oeste")
    );

    /**
     * Obtiene la calidad del aire de todas las estaciones de Medellín.
     */
    public Flux<AirQualityData> getAllStations() {
        return Flux.fromIterable(MEDELLIN_STATIONS)
                .flatMap(station -> getStationData(station.id(), station.name(), station.zone()))
                .doOnError(e -> log.warn("[AirQuality] Error obteniendo estaciones: {}", e.getMessage()));
    }

    /**
     * Obtiene datos de una estación específica por nombre o ID.
     */
    public Mono<AirQualityData> getStation(String query) {
        return webClient.get()
                .uri("/feed/{query}/?token={token}", query, token)
                .retrieve()
                .bodyToMono(WaqiResponse.class)
                .map(resp -> mapToAirQuality(resp, query, query))
                .doOnError(e -> log.warn("[AirQuality] Error estación {}: {}", query, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * Obtiene el AQI general de Medellín (feed/geolocation de WAQI).
     */
    public Mono<AirQualityData> getMedellinAqi() {
        return webClient.get()
                .uri("/feed/medellin/?token={token}", token)
                .retrieve()
                .bodyToMono(WaqiResponse.class)
                .map(resp -> mapToAirQuality(resp, "medellin", "Medellín General"))
                .doOnError(e -> log.warn("[AirQuality] Error AQI Medellín: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    private Mono<AirQualityData> getStationData(String id, String name, String zone) {
        return webClient.get()
                .uri("/feed/{id}/?token={token}", id, token)
                .retrieve()
                .bodyToMono(WaqiResponse.class)
                .map(resp -> mapToAirQuality(resp, id, name + " (" + zone + ")"))
                .doOnError(e -> log.debug("[AirQuality] Estación {} no disponible: {}", name, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    private AirQualityData mapToAirQuality(WaqiResponse resp, String stationId, String displayName) {
        if (resp == null || resp.data == null) return null;
        var d = resp.data;
        var iaqi = d.iaqi != null ? d.iaqi : new Iaqi();

        return new AirQualityData(
                stationId,
                displayName,
                d.aqi,
                getAqiLevel(d.aqi),
                getAqiColor(d.aqi),
                getAqiRecommendation(d.aqi),
                iaqi.pm25 != null ? iaqi.pm25.v : null,
                iaqi.pm10 != null ? iaqi.pm10.v : null,
                iaqi.o3 != null ? iaqi.o3.v : null,
                iaqi.no2 != null ? iaqi.no2.v : null,
                iaqi.so2 != null ? iaqi.so2.v : null,
                iaqi.co != null ? iaqi.co.v : null,
                d.time != null ? d.time.iso : null,
                d.aqi > 100  // nivel no saludable
        );
    }

    private String getAqiLevel(int aqi) {
        if (aqi <= 50) return "Buena";
        if (aqi <= 100) return "Moderada";
        if (aqi <= 150) return "Dañina para grupos sensibles";
        if (aqi <= 200) return "Dañina";
        if (aqi <= 300) return "Muy dañina";
        return "Peligrosa";
    }

    private String getAqiColor(int aqi) {
        if (aqi <= 50) return "#00e400";   // Verde
        if (aqi <= 100) return "#ffff00";  // Amarillo
        if (aqi <= 150) return "#ff7e00";  // Naranja
        if (aqi <= 200) return "#ff0000";  // Rojo
        if (aqi <= 300) return "#8f3f97";  // Púrpura
        return "#7e0023";                  // Granate
    }

    private String getAqiRecommendation(int aqi) {
        if (aqi <= 50) return "Calidad del aire satisfactoria. Disfruta al aire libre.";
        if (aqi <= 100) return "Calidad aceptable. Los sensibles pueden reducir esfuerzos al aire libre.";
        if (aqi <= 150) return "Grupos sensibles (niños, ancianos, asmáticos) deben reducir actividad al aire libre.";
        if (aqi <= 200) return "Todos pueden experimentar efectos. Evita actividad prolongada al aire libre.";
        if (aqi <= 300) return "Alerta de salud: evita todo esfuerzo al aire libre.";
        return "Emergencia sanitaria: permanece en interiores.";
    }

    // ── DTOs ──────────────────────────────────────────────────────────────

    public record AirQualityData(
            String stationId,
            String stationName,
            int aqi,
            String level,
            String color,
            String recommendation,
            Double pm25,
            Double pm10,
            Double o3,
            Double no2,
            Double so2,
            Double co,
            String lastUpdate,
            boolean unhealthy
    ) {}

    public record WaqiResponse(
            String status,
            WaqiData data
    ) {}

    public record WaqiData(
            int aqi,
            Station station,
            Iaqi iaqi,
            TimeInfo time
    ) {}

    public record Station(
            String name,
            Geo geo,
            String url
    ) {}

    public record Geo(List<Double> coordinates) {}

    public record Iaqi(
            Val pm25,
            Val pm10,
            Val o3,
            Val no2,
            Val so2,
            Val co
    ) {
        public Iaqi() { this(null, null, null, null, null, null); }
    }

    public record Val(double v) {}

    public record TimeInfo(String iso) {}
}
