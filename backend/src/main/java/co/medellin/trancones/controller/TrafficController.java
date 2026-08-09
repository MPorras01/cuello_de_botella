package co.medellin.trancones.controller;

import co.medellin.trancones.dto.*;
import co.medellin.trancones.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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

    /**
     * Último snapshot válido (con datos). Se sirve como caché cuando un ciclo
     * no logra datos de ninguna fuente (p. ej. cuota externa agotada), para
     * que el mapa nunca quede en blanco. Se marca stale = true.
     */
    private final AtomicReference<TrafficSnapshotDTO> lastGoodSnapshot = new AtomicReference<>();

    /**
     * Intervalo entre ciclos SSE (segundos). Configurable para ajustar el
     * consumo de la cuota de las APIs externas (TomTom free ≈ 2.500/día).
     */
    @Value("${traffic.refresh-interval-seconds:30}")
    private long refreshIntervalSeconds;

    // ─── SSE ─────────────────────────────────────────────────────────────────

    /**
     * GET /api/stream/traffic — emite TrafficSnapshotDTO como SSE cada 30 s.
     * Requisitos: 3.1, 3.2, 3.3, 3.4, 10.1, 10.2, 10.3, 11.1, 11.2
     */
    @GetMapping(value = "/stream/traffic", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<TrafficSnapshotDTO>> streamTraffic() {
        return Flux.concat(
                        // Evento inmediato al conectar: caché si existe, si no ciclo fresco.
                        firstEvent(),
                        Flux.interval(Duration.ofSeconds(Math.max(10, refreshIntervalSeconds)))
                                .flatMap(tick -> buildSnapshot()))
                .map(snapshot -> ServerSentEvent.<TrafficSnapshotDTO>builder()
                        .event("traffic-update")
                        .data(snapshot)
                        .build());
    }

    /**
     * Primer evento del stream: sirve el último snapshot conocido al instante;
     * si aún no hay caché (primer cliente tras arranque), dispara un ciclo de
     * agregación inmediato para no esperar al primer intervalo.
     */
    private Mono<TrafficSnapshotDTO> firstEvent() {
        return Mono.defer(() -> {
            TrafficSnapshotDTO cached = lastGoodSnapshot.get();
            if (cached != null) {
                log.info("[Controller] Cliente conectado — enviando último snapshot ({} segmentos)",
                        cached.segments().size());
                return Mono.just(cached);
            }
            log.info("[Controller] Primer cliente — disparando ciclo inmediato");
            return buildSnapshot();
        });
    }

    /** Ejecuta un ciclo de agregación y aplica la lógica de caché/fusión. */
    private Mono<TrafficSnapshotDTO> buildSnapshot() {
        long start = System.currentTimeMillis();
        return aggregatorService.getAggregatedTraffic()
                .flatMap(agg -> {
                    List<SegmentStatus> bottlenecks = bottleneckService.detectAll(agg.segments());
                    TrafficSnapshotDTO cached = lastGoodSnapshot.get();
                    TrafficSnapshotDTO snapshot;
                    if (!agg.segments().isEmpty()) {
                        // Ciclo sano: se refresca la caché con segmentos + alertas.
                        snapshot = TrafficSnapshotDTO.fresh(agg.segments(), bottlenecks, agg.alerts());
                        lastGoodSnapshot.set(snapshot);
                    } else if (!agg.alerts().isEmpty()) {
                        // Fallaron los segmentos pero hay alertas frescas: se mantienen
                        // los segmentos en caché y se fusionan con las alertas nuevas.
                        snapshot = cached != null && !cached.segments().isEmpty()
                                ? new TrafficSnapshotDTO(cached.segments(), cached.bottlenecks(),
                                        agg.alerts(), true)
                                : TrafficSnapshotDTO.fresh(List.of(), List.of(), agg.alerts());
                        if (cached != null && !cached.segments().isEmpty()) {
                            log.warn("[Controller] Sin segmentos de fuentes externas — segmentos en caché + alertas frescas");
                        }
                    } else {
                        // Sin datos de ninguna fuente: servir el último válido como caché.
                        snapshot = cached != null
                                ? new TrafficSnapshotDTO(cached.segments(), cached.bottlenecks(),
                                        cached.alerts(), true)
                                : TrafficSnapshotDTO.fresh(List.of(), List.of(), List.of());
                        if (cached != null) {
                            log.warn("[Controller] Sin datos de fuentes externas — sirviendo snapshot en caché");
                        }
                    }
                    return cacheAndNotify(snapshot, start);
                })
                // Un error transitorio (timeout de WebClient, Redis caído que escape del
                // onErrorResume interno) NO debe matar el stream SSE: se sirve el último
                // snapshot en caché (o uno vacío) y el siguiente ciclo reintenta solo.
                .onErrorResume(e -> {
                    log.warn("[Controller] Error en ciclo de agregación — sirviendo snapshot en caché: {}",
                            e.getMessage());
                    TrafficSnapshotDTO cached = lastGoodSnapshot.get();
                    return Mono.just(cached != null
                            ? new TrafficSnapshotDTO(cached.segments(), cached.bottlenecks(),
                                    cached.alerts(), true)
                            : TrafficSnapshotDTO.fresh(List.of(), List.of(), List.of()));
                });
    }

    private Mono<TrafficSnapshotDTO> cacheAndNotify(TrafficSnapshotDTO snapshot, long startMs) {
        SegmentStatus worst = snapshot.bottlenecks() == null || snapshot.bottlenecks().isEmpty()
                ? null
                : snapshot.bottlenecks().get(0);

        // Snapshot en caché: no re-notificar ni reescribir Redis (datos sin cambios).
        if (snapshot.stale()) {
            return Mono.fromSupplier(() -> snapshot);
        }

        // Cachear en Redis con TTL 60s (onErrorResume para fallback si Redis no está disponible)
        Mono<Void> cacheOp = Flux.fromIterable(snapshot.segments())
                .flatMap(seg -> redisTemplate.opsForValue()
                        .set("traffic:segment:" + seg.segmentId(),
                                seg.segmentId() + ":" + seg.speedRatio(),
                                Duration.ofSeconds(60)))
                .then(worst != null
                        ? redisTemplate.opsForValue()
                                .set("traffic:bottleneck", worst.segmentId(),
                                        Duration.ofSeconds(60)).then()
                        : Mono.empty())
                .onErrorResume(e -> {
                    log.warn("[Controller] Redis no disponible, continuando sin caché: {}", e.getMessage());
                    return Mono.empty();
                });

        // Enviar notificación push si hay cuello de botella severo
        Mono<Void> notifyOp = worst != null
                ? notificationService.notifyBottleneck(worst).onErrorResume(e -> Mono.empty())
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
