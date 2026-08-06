package co.medellin.trancones.service;

import co.medellin.trancones.dto.SegmentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

/**
 * Detecta el cuello de botella de la malla vial.
 * El cuello de botella es el segmento con el menor speedRatio.
 * En caso de empate, se selecciona el segmento con el segmentId lexicográficamente menor.
 * Requisitos: 2.1, 2.2, 2.3, 2.4
 */
@Slf4j
@Service
public class BottleneckDetectorService {

    /**
     * Detecta el cuello de botella de la lista de segmentos dada.
     *
     * @param segments lista de segmentos del ciclo actual (puede estar vacía)
     * @return Mono con el segmento de menor speedRatio, o Mono.empty() si la lista está vacía
     */
    public Mono<SegmentStatus> detect(List<SegmentStatus> segments) {
        if (segments == null || segments.isEmpty()) {
            log.debug("[Bottleneck] Lista de segmentos vacía, retornando Mono.empty()");
            return Mono.empty();
        }

        // Comparador: primero por speedRatio ascendente, luego por segmentId lexicográfico
        Comparator<SegmentStatus> comparator = Comparator
                .comparingDouble(SegmentStatus::speedRatio)
                .thenComparing(SegmentStatus::segmentId);

        return segments.stream()
                .filter(s -> s.speedRatio() != null)
                .min(comparator)
                .map(bottleneck -> {
                    log.info("[Bottleneck] Cuello de botella detectado: segmentId={}, speedRatio={}, nivel={}",
                            bottleneck.segmentId(), bottleneck.speedRatio(), bottleneck.congestionLevel());
                    return Mono.just(bottleneck);
                })
                .orElse(Mono.empty());
    }
}
