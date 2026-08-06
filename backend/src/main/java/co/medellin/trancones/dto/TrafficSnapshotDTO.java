package co.medellin.trancones.dto;

import java.util.List;

/**
 * Payload del evento SSE emitido por /api/stream/traffic.
 * bottleneck puede ser null si no hay datos en el ciclo actual.
 */
public record TrafficSnapshotDTO(
    List<SegmentStatus> segments,
    SegmentStatus bottleneck
) {}
