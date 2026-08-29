package co.medellin.trancones.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Servicio de clima en tiempo real usando Open-Meteo (gratis, sin API key).
 * Proporciona: temperatura, lluvia, viento, humedad, código del clima.
 * Coordenadas del Valle de Aburrá (Medellín): lat 6.2476, lon -75.5658
 */
@Slf4j
@Service
public class WeatherService {

    private static final String BASE_URL = "https://api.open-meteo.com/v1";
    private static final double MEDELLIN_LAT = 6.2476;
    private static final double MEDELLIN_LON = -75.5658;

    private final WebClient webClient = WebClient.builder()
            .baseUrl(BASE_URL)
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();

    /**
     * Obtiene el clima actual de Medellín.
     */
    public Mono<WeatherData> getCurrentWeather() {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/forecast")
                        .queryParam("latitude", MEDELLIN_LAT)
                        .queryParam("longitude", MEDELLIN_LON)
                        .queryParam("current", "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,rain,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m")
                        .queryParam("timezone", "America/Bogota")
                        .queryParam("forecast_days", "1")
                        .build())
                .retrieve()
                .bodyToMono(OpenMeteoWeatherResponse.class)
                .map(this::mapToWeatherData)
                .doOnError(e -> log.warn("[Weather] Error consultando Open-Meteo: {}", e.getMessage()))
                .onErrorResume(e -> {
                    log.debug("[Weather] Fallback: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Obtiene pronóstico por horas (próximas 24h).
     */
    public Mono<HourlyForecast> getHourlyForecast() {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/forecast")
                        .queryParam("latitude", MEDELLIN_LAT)
                        .queryParam("longitude", MEDELLIN_LON)
                        .queryParam("hourly", "temperature_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m")
                        .queryParam("timezone", "America/Bogota")
                        .queryParam("forecast_days", "2")
                        .build())
                .retrieve()
                .bodyToMono(OpenMeteoHourlyResponse.class)
                .map(this::mapToHourlyForecast)
                .doOnError(e -> log.warn("[Weather] Error pronóstico horario: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    private WeatherData mapToWeatherData(OpenMeteoWeatherResponse resp) {
        if (resp == null || resp.current == null) return null;
        var c = resp.current;
        return new WeatherData(
                c.temperature,
                c.apparentTemperature,
                c.humidity,
                c.precipitation,
                c.rain,
                c.windSpeed,
                c.windDirection,
                c.windGusts,
                c.weatherCode,
                describeWeatherCode(c.weatherCode),
                isSevereWeather(c)
        );
    }

    private HourlyForecast mapToHourlyForecast(OpenMeteoHourlyResponse resp) {
        if (resp == null || resp.hourly == null) return null;
        var h = resp.hourly;
        int count = Math.min(h.time.size(), 24);
        return new HourlyForecast(
                h.time.subList(0, count),
                h.temperature.subList(0, count),
                h.precipitationProbability.subList(0, count),
                h.precipitation.subList(0, count),
                h.weatherCode.subList(0, count),
                h.windSpeed.subList(0, count)
        );
    }

    private boolean isSevereWeather(CurrentWeather c) {
        return c.precipitation > 5.0      // lluvia fuerte
                || c.windGusts > 50.0      // ráfagas peligrosas
                || c.weatherCode >= 95;    // tormentas
    }

    private String describeWeatherCode(int code) {
        return switch (code) {
            case 0 -> "☀️ Despejado";
            case 1, 2, 3 -> "⛅ Parcialmente nublado";
            case 45, 48 -> "🌫️ Neblina";
            case 51, 53, 55 -> "🌦️ Llovizna";
            case 56, 57 -> "🌧️ Llovizna congelada";
            case 61, 63, 65 -> "🌧️ Lluvia";
            case 66, 67 -> "🌧️ Lluvia congelada";
            case 71, 73, 75 -> "❄️ Nieve";
            case 77 -> "❄️ Granizo";
            case 80, 81, 82 -> "🌦️ Chaparrones";
            case 85, 86 -> "🌨️ Tormenta de nieve";
            case 95 -> "⛈️ Tormenta eléctrica";
            case 96, 99 -> "⛈️ Tormenta con granizo";
            default -> "🌡️ Clima desconocido";
        };
    }

    // ── DTOs internos ─────────────────────────────────────────────────────

    public record WeatherData(
            double temperature,
            double apparentTemperature,
            int humidity,
            double precipitation,
            double rain,
            double windSpeed,
            int windDirection,
            double windGusts,
            int weatherCode,
            String description,
            boolean severe
    ) {}

    public record HourlyForecast(
            List<String> times,
            List<Double> temperatures,
            List<Integer> precipitationProbability,
            List<Double> precipitation,
            List<Integer> weatherCodes,
            List<Double> windSpeeds
    ) {}

    public record OpenMeteoWeatherResponse(CurrentWeather current) {}
    public record CurrentWeather(
            @JsonProperty("temperature_2m") double temperature,
            @JsonProperty("apparent_temperature") double apparentTemperature,
            @JsonProperty("relative_humidity_2m") int humidity,
            @JsonProperty("precipitation") double precipitation,
            @JsonProperty("rain") double rain,
            @JsonProperty("weather_code") int weatherCode,
            @JsonProperty("wind_speed_10m") double windSpeed,
            @JsonProperty("wind_direction_10m") int windDirection,
            @JsonProperty("wind_gusts_10m") double windGusts
    ) {}

    public record OpenMeteoHourlyResponse(HourlyData hourly) {}
    public record HourlyData(
            List<String> time,
            @JsonProperty("temperature_2m") List<Double> temperature,
            @JsonProperty("precipitation_probability") List<Integer> precipitationProbability,
            @JsonProperty("precipitation") List<Double> precipitation,
            @JsonProperty("weather_code") List<Integer> weatherCode,
            @JsonProperty("wind_speed_10m") List<Double> windSpeed
    ) {}
}
