package co.medellin.trancones.dto;

import java.util.List;

/**
 * Payload del evento SSE emitido por /api/stream/traffic.
 * bottlenecks contiene TODOS los cuellos de botella del ciclo (peor primero),
 * puede estar vacío. alerts contiene los incidentes (policía, accidentes, etc.).
 * stale = true indica que el payload es el último snapshot válido en caché
 * (por ejemplo, cuando una fuente externa excedió su cuota) y no datos nuevos.
 */
public record TrafficSnapshotDTO(
    List<SegmentStatus> segments,
    List<SegmentStatus> bottlenecks,
    List<TrafficAlert> alerts,
    boolean stale
) {
    public TrafficSnapshotDTO {
        if (segments == null) segments = List.of();
        if (bottlenecks == null) bottlenecks = List.of();
        if (alerts == null) alerts = List.of();
    }

    /** Conveniencia: snapshot fresco (stale = false). */
    public static TrafficSnapshotDTO fresh(
            List<SegmentStatus> segments, List<SegmentStatus> bottlenecks, List<TrafficAlert> alerts) {
        return new TrafficSnapshotDTO(segments, bottlenecks, alerts, false);
    }
}
