package co.medellin.trancones.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO de parseo de Nominatim Reverse Geocode (OpenStreetMap).
 * Extrae nombre de calle, barrio, municipio y otras referencias
 * para enriquecer las alertas de tráfico con ubicación textual.
 * API: https://nominatim.openstreetmap.org/reverse
 */
public record NominatimResponse(
    @JsonProperty("display_name") String displayName,
    @JsonProperty("address") Address address
) {
    public record Address(
        @JsonProperty("road") String road,
        @JsonProperty("pedestrian") String pedestrian,
        @JsonProperty("highway") String highway,
        @JsonProperty("suburb") String suburb,
        @JsonProperty("neighbourhood") String neighbourhood,
        @JsonProperty("city") String city,
        @JsonProperty("town") String town,
        @JsonProperty("village") String village,
        @JsonProperty("municipality") String municipality,
        @JsonProperty("state") String state,
        @JsonProperty("country") String country,
        @JsonProperty("postcode") String postcode,
        @JsonProperty("house_number") String houseNumber
    ) {}
}
