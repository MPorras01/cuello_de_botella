package co.medellin.trancones.dto.external;

import java.util.List;

/**
 * DTO de parseo de TomTom Traffic Flow API (flowSegmentData).
 * {@code currentSpeed} y {@code freeFlowSpeed} vienen en km/h.
 */
public record TomTomFlowResponse(FlowSegmentData flowSegmentData) {

    public record FlowSegmentData(
        Double currentSpeed,
        Double freeFlowSpeed,
        Double confidence,
        Coordinates coordinates
    ) {}

    public record Coordinates(List<Coordinate> coordinate) {}

    public record Coordinate(double latitude, double longitude) {}
}
