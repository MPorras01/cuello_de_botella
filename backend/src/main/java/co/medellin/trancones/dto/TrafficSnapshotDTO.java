package co.medellin.trancones.dto;

import java.util.List;

/**
 * Payload del evento SSE emitido por /api/stream/traffic.
 * bottlenecks contiene TODOS los cuellos de botella del ciclo (peor primero),
 * puede estar vacío. alerts contiene los incidentes (policía, accidentes, etc.).
 */
public record TrafficSnapshotDTO(
    List<SegmentStatus> segments,
    List<SegmentStatus> bottlenecks,
    List<TrafficAlert> alerts
) {}
