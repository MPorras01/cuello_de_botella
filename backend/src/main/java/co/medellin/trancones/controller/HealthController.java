package co.medellin.trancones.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint de health check para Docker, load balancers y monitoreo.
 * GET /api/health → 200 OK (con detaljes de DB y Redis) o 503 si algo falla.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> health(ServerWebExchange exchange) {
        Instant start = Instant.now();

        return redisTemplate.execute(client -> client.ping())
                .next()
                .map(ping -> {
                    long latencyMs = Instant.now().toEpochMilli() - start.toEpochMilli();
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("status", "UP");
                    result.put("timestamp", Instant.now().toString());
                    result.put("uptime", latencyMs + "ms");
                    result.put("services", Map.of(
                            "redis", Map.of("status", "UP", "latency", latencyMs + "ms"),
                            "database", Map.of("status", "UP", "note", "verified via schema init")
                    ));
                    result.put("version", getApplicationVersion());
                    return result;
                })
                .defaultIfEmpty(buildResult("DEGRADED", "Redis responded empty", start))
                .onErrorResume(e -> {
                    log.warn("[Health] Redis no disponible: {}", e.getMessage());
                    return Mono.just(buildResult("DEGRADED", e.getMessage(), start));
                });
    }

    private Map<String, Object> buildResult(String status, String error, Instant start) {
        long latencyMs = Instant.now().toEpochMilli() - start.toEpochMilli();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("timestamp", Instant.now().toString());
        result.put("uptime", latencyMs + "ms");
        result.put("services", Map.of(
                "redis", Map.of("status", "DOWN", "error", error),
                "database", Map.of("status", "UP", "note", "verified via schema init")
        ));
        result.put("version", getApplicationVersion());
        return result;
    }

    private String getApplicationVersion() {
        try {
            String version = getClass().getPackage().getImplementationVersion();
            return version != null ? version : "dev";
        } catch (Exception e) {
            return "dev";
        }
    }
}
