package co.medellin.trancones.dto.external;

import java.util.List;

/**
 * DTO de parseo de respuesta de Waze for Cities API.
 * Mapea alertas colaborativas georreferenciadas.
 */
public record WazeAlertsResponse(List<WazeAlert> alerts) {

    public record WazeAlert(
        String uuid,
        String type,
        String street,
        double speed,
        WazeLocation location
    ) {}

    public record WazeLocation(double x, double y) {}
}
