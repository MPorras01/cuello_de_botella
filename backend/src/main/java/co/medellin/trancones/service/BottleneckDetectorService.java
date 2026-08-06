package co.medellin.trancones.service;

import co.medellin.trancones.dto.SegmentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Detecta los cuellos de botella de la malla vial.
 * Un cuello de botella es cualquier segmento con speedRatio bajo el umbral
 * (velocidad real / velocidad libre), es decir con nivel "lento" o peor.
 * Requisitos: 2.1, 2.2, 2.3, 2.4
 */
@Slf4j
@Service
public class BottleneckDetectorService {

    /** Umbral de speedRatio bajo el cual un segmento se considera cuello de botella. */
    private static final double BOTTLENECK_THRESHOLD = 0.5;

    /** Comparador: peor primero (menor speedRatio), desempate por segmentId. */
    private static final Comparator<SegmentStatus> WORST_FIRST = Comparator
            .comparingDouble(SegmentStatus::speedRatio)
            .thenComparing(SegmentStatus::segmentId);

    /**
     * Detecta TODOS los cuellos de botella de la lista de segmentos dada,
     * ordenados del peor al menos grave. Permite que el mapa muestre varios
     * cuellos de botella a la vez.
     *
     * @param segments lista de segmentos del ciclo actual (puede estar vacía)
     * @return lista de segmentos con speedRatio < umbral, peor primero; vacía si no hay
     */
    public List<SegmentStatus> detectAll(List<SegmentStatus> segments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        List<SegmentStatus> bottlenecks = segments.stream()
                .filter(s -> s.speedRatio() != null && s.speedRatio() < BOTTLENECK_THRESHOLD)
                .sorted(WORST_FIRST)
                .collect(Collectors.toList());
        if (!bottlenecks.isEmpty()) {
            log.info("[Bottleneck] {} cuello(s) de botella: {}", bottlenecks.size(),
                    bottlenecks.stream().map(SegmentStatus::segmentId).collect(Collectors.joining(", ")));
        }
        return bottlenecks;
    }

    /**
     * Detecta el cuello de botella más grave de la lista (compatibilidad con
     * notificaciones push y rutas alternativas).
     *
     * @param segments lista de segmentos del ciclo actual (puede estar vacía)
     * @return Mono con el segmento de menor speedRatio, o Mono.empty() si la lista está vacía
     */
    public Mono<SegmentStatus> detectWorst(List<SegmentStatus> segments) {
        if (segments == null || segments.isEmpty()) {
            return Mono.empty();
        }
        return segments.stream()
                .filter(s -> s.speedRatio() != null)
                .min(WORST_FIRST)
                .map(Mono::just)
                .orElse(Mono.empty());
    }
}
