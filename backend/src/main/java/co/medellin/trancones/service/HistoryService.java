package co.medellin.trancones.service;

import co.medellin.trancones.dto.HistorySummary;
import co.medellin.trancones.dto.SegmentStatus;
import co.medellin.trancones.model.TrafficHistory;
import co.medellin.trancones.repository.TrafficHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Persiste snapshots del estado de tráfico cada 5 minutos y expone consultas históricas.
 * Requisitos: 6.1, 6.2, 6.3, 6.4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final TrafficHistoryRepository historyRepository;

    // Referencia al agregador para obtener el estado actual en el snapshot programado
    private final TrafficAggregatorService aggregatorService;

    /**
     * Tarea programada: persiste un snapshot del estado de todos los segmentos cada 5 minutos.
     * Requisito 6.1
     */
    @Scheduled(fixedDelayString = "PT5M")
    public void scheduleSnapshot() {
        aggregatorService.getAggregatedTraffic()
                .flatMap(agg -> {
                    log.info("[History] Persistiendo snapshot con {} segmentos", agg.segments().size());
                    return persistSnapshot(agg.segments());
                })
                .subscribe(
                        count -> log.info("[History] Snapshot persistido: {} registros", count),
                        error -> log.error("[History] Error persistiendo snapshot: {}", error.getMessage(), error)
                );
    }

    /**
     * Persiste el snapshot de la lista de segmentos dada.
     * Requisito 6.2
     *
     * @param currentSegments lista de segmentos del ciclo actual
     * @return Mono con el número de registros persistidos
     */
    public Mono<Long> persistSnapshot(List<SegmentStatus> currentSegments) {
        if (currentSegments == null || currentSegments.isEmpty()) {
            return Mono.just(0L);
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<TrafficHistory> records = currentSegments.stream()
                .map(s -> TrafficHistory.builder()
                        .segmentId(s.segmentId())
                        .segmentName(s.segmentName())
                        .speedRatio(s.speedRatio())
                        .congestionLevel(s.congestionLevel())
                        .timestamp(now)
                        .build())
                .collect(Collectors.toList());
        return historyRepository.saveAll(records)
                .count()
                .doOnError(e -> log.error("[History] Error al guardar en BD: {}", e.getMessage(), e));
    }

    /**
     * Consulta el histórico de congestión agrupado por hora (day) o por día (week).
     * Requisito 6.3, 6.4
     *
     * @param range "day" (últimas 24h) o "week" (últimos 7 días)
     * @return Flux de HistorySummary con etiquetas en español colombiano
     */
    public Flux<HistorySummary> getHistory(String range) {
        OffsetDateTime from = switch (range) {
            case "day" -> OffsetDateTime.now(ZoneOffset.UTC).minusHours(24);
            case "week" -> OffsetDateTime.now(ZoneOffset.UTC).minusDays(7);
            default -> throw new IllegalArgumentException("range");
        };

        return historyRepository.findByTimestampAfter(from)
                .collectList()
                .flatMapMany(records -> {
                    if ("day".equals(range)) {
                        return buildDaySummaries(records);
                    } else {
                        return buildWeekSummaries(records);
                    }
                });
    }

    // ─── Agrupación por hora ────────────────────────────────────────────────

    private Flux<HistorySummary> buildDaySummaries(List<TrafficHistory> records) {
        // Agrupa por hora del día (0-23) y calcula speedRatio promedio
        Map<Integer, List<TrafficHistory>> byHour = records.stream()
                .collect(Collectors.groupingBy(r ->
                        r.getTimestamp().withOffsetSameInstant(ZoneOffset.of("-05:00")).getHour()));

        List<HistorySummary> summaries = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            List<TrafficHistory> hourRecords = byHour.getOrDefault(hour, List.of());
            double avg = hourRecords.isEmpty() ? 1.0
                    : hourRecords.stream().mapToDouble(TrafficHistory::getSpeedRatio).average().orElse(1.0);
            summaries.add(new HistorySummary(
                    String.format("%02d:00", hour),
                    Math.round(avg * 1000.0) / 1000.0,
                    congestionLevel(avg)));
        }
        return Flux.fromIterable(summaries);
    }

    // ─── Agrupación por día ─────────────────────────────────────────────────

    private Flux<HistorySummary> buildWeekSummaries(List<TrafficHistory> records) {
        // Agrupa por día de la semana (MONDAY=1..SUNDAY=7)
        Map<DayOfWeek, List<TrafficHistory>> byDay = records.stream()
                .collect(Collectors.groupingBy(r ->
                        r.getTimestamp().withOffsetSameInstant(ZoneOffset.of("-05:00")).getDayOfWeek()));

        // Orden: lunes a domingo
        DayOfWeek[] order = {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        };

        List<HistorySummary> summaries = new ArrayList<>();
        for (DayOfWeek day : order) {
            List<TrafficHistory> dayRecords = byDay.getOrDefault(day, List.of());
            double avg = dayRecords.isEmpty() ? 1.0
                    : dayRecords.stream().mapToDouble(TrafficHistory::getSpeedRatio).average().orElse(1.0);
            summaries.add(new HistorySummary(
                    dayNameSpanish(day),
                    Math.round(avg * 1000.0) / 1000.0,
                    congestionLevel(avg)));
        }
        return Flux.fromIterable(summaries);
    }

    // ─── Utilidades ─────────────────────────────────────────────────────────

    private static String dayNameSpanish(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "Lunes";
            case TUESDAY -> "Martes";
            case WEDNESDAY -> "Miércoles";
            case THURSDAY -> "Jueves";
            case FRIDAY -> "Viernes";
            case SATURDAY -> "Sábado";
            case SUNDAY -> "Domingo";
        };
    }

    private static String congestionLevel(double speedRatio) {
        if (speedRatio >= 0.70) return "fluido";
        if (speedRatio >= 0.30) return "moderado";
        return "severo";
    }
}
