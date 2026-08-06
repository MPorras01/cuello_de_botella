package co.medellin.trancones.controller;

import co.medellin.trancones.dto.*;
import co.medellin.trancones.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Controlador reactivo. Expone endpoints SSE y REST.
 * Requisitos: 3.1–3.4, 5.1, 6.3–6.5, 8.2, 8.3, 10.1–10.3, 11.1, 11.2
 * Todos los endpoints requieren autenticación JWT (ver SecurityConfig).
 */
@Slf4j
@RestController
@RequestMapping("/api")
@Validated
@RequiredArgsConstructor
public class TrafficController {

    private final TrafficAggregatorService aggregatorService;
    private final BottleneckDetectorService bottleneckService;
    private final HistoryService historyService;
    private final NotificationService notificationService;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    // ─── SSE ─────────────────────────────────────────────────────────────────

    /**
     * GET /api/stream/traffic — emite TrafficSnapshotDTO como SSE cada 30 s.
     * Requisitos: 3.1, 3.2, 3.3, 3.4, 10.1, 10.2, 10.3, 11.1, 11.2
     */
    @GetMapping(value = "/stream/traffic", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<TrafficSnapshotDTO>> streamTraffic() {
        return Flux.interval(Duration.ofSeconds(30))
                .flatMap(tick -> {
                    long start = System.currentTimeMillis();
                    return aggregatorService.getAggregatedTraffic()
                            .collectList()
                            .flatMap(segments ->
                                    bottleneckService.detect(segments)
                                            .map(bn -> new TrafficSnapshotDTO(segments, bn))
                                            .defaultIfEmpty(new TrafficSnapshotDTO(segments, null))
                                            .flatMap(snapshot -> cacheAndNotify(snapshot, start))
                            );
                })
                .map(snapshot -> ServerSentEvent.<TrafficSnapshotDTO>builder()
                        .event("traffic-update")
                        .data(snapshot)
                        .build());
    }

    private Mono<TrafficSnapshotDTO> cacheAndNotify(TrafficSnapshotDTO snapshot, long startMs) {
        // Cachear en Redis con TTL 60s (onErrorResume para fallback si Redis no está disponible)
        Mono<Void> cacheOp = Flux.fromIterable(snapshot.segments())
                .flatMap(seg -> redisTemplate.opsForValue()
                        .set("traffic:segment:" + seg.segmentId(),
                                seg.segmentId() + ":" + seg.speedRatio(),
                                Duration.ofSeconds(60)))
                .then(snapshot.bottleneck() != null
                        ? redisTemplate.opsForValue()
                                .set("traffic:bottleneck", snapshot.bottleneck().segmentId(),
                                        Duration.ofSeconds(60)).then()
                        : Mono.empty())
                .onErrorResume(e -> {
                    log.warn("[Controller] Redis no disponible, continuando sin caché: {}", e.getMessage());
                    return Mono.empty();
                });

        // Enviar notificación push si hay cuello de botella severo
        Mono<Void> notifyOp = snapshot.bottleneck() != null
                ? notificationService.notifyBottleneck(snapshot.bottleneck()).onErrorResume(e -> Mono.empty())
                : Mono.empty();

        return cacheOp.then(notifyOp)
                .then(Mono.fromSupplier(() -> {
                    log.info("[Controller] Ciclo SSE: {} segmentos, duración {} ms",
                            snapshot.segments().size(), System.currentTimeMillis() - startMs);
                    return snapshot;
                }));
    }

    // ─── Rutas alternativas ──────────────────────────────────────────────────

    /**
     * GET /api/routes/alternatives?segmentId={id}
     * Requisito 5.1
     */
    @GetMapping("/routes/alternatives")
    public Mono<AlternativeRoutesResponse> getAlternatives(
            @RequestParam @NotBlank(message = "segmentId es obligatorio")
            @Size(max = 200, message = "segmentId no puede exceder 200 caracteres") String segmentId) {
        return aggregatorService.getAlternativeRoutes(segmentId);
    }

    // ─── Histórico ───────────────────────────────────────────────────────────

    /**
     * GET /api/history?range=day|week
     * Requisitos: 6.3, 6.4, 6.5
     */
    @GetMapping("/history")
    public ResponseEntity<Flux<HistorySummary>> getHistory(
            @RequestParam(defaultValue = "week")
            @Pattern(regexp = "day|week", message = "range debe ser 'day' o 'week'") String range) {
        return ResponseEntity.ok(historyService.getHistory(range));
    }

    // ─── Notificaciones Push ─────────────────────────────────────────────────

    /**
     * POST /api/push/subscribe — registra suscripción VAPID.
     * Requisito 8.2
     */
    @PostMapping("/push/subscribe")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Mono<Void> subscribe(@Valid @RequestBody PushSubscriptionRequest request) {
        return notificationService.subscribe(request);
    }

    /**
     * DELETE /api/push/unsubscribe — cancela suscripción VAPID.
     * Requisito 8.3
     */
    @DeleteMapping("/push/unsubscribe")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public Mono<Void> unsubscribe(
            @RequestParam @NotBlank(message = "endpoint es obligatorio")
            @Size(max = 2048, message = "endpoint no puede exceder 2048 caracteres") String endpoint) {
        return notificationService.unsubscribe(endpoint);
    }
}
