package co.medellin.trancones.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuración de las fuentes de tráfico (prefijo {@code traffic.sources}).
 * Cada fuente puede activarse/desactivarse según las variables de entorno
 * disponibles (ver .env.example).
 */
@Data
@Component
@ConfigurationProperties(prefix = "traffic.sources")
public class TrafficProperties {

    private Google google = new Google();
    private Waze waze = new Waze();
    private Simm simm = new Simm();
    private Tomtom tomtom = new Tomtom();

    /** Google Maps Routes API — requiere clave con Routes API habilitada. */
    @Data
    public static class Google {
        private String apiKey;
        private String baseUrl = "https://routes.googleapis.com";
    }

    /** Waze for Cities — requiere aprobación institucional (partner feed URL). */
    @Data
    public static class Waze {
        private String baseUrl;
    }

    /** SIMM / Datos abiertos Medellín (CKAN datastore_search, público). */
    @Data
    public static class Simm {
        private String baseUrl = "https://datosabiertos.medellin.gov.co/api";
        private String resourceId;
        private Fields fields = new Fields();

        @Data
        public static class Fields {
            private String speed = "velocidad";
            private String freeFlow = "velocidad_libre";
            private String name = "direccion";
            private String lat = "lat";
            private String lng = "lng";
        }
    }

    /** TomTom Traffic API — free tier sin aprobación (flujo en tiempo real). */
    @Data
    public static class Tomtom {
        private String apiKey;
        private String baseUrl = "https://api.tomtom.com";
        private List<Point> points = new ArrayList<>();

        @Data
        public static class Point {
            private String name;
            private double lat;
            private double lon;
        }
    }
}
