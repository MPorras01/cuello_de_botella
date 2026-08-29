package co.medellin.trancones.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO de parseo de TomTom Reverse Geocode API (search/2/reverseGeocode).
 * Extrae el nombre de la calle, barrio, municipio y其它 referencias
 * para enriquecer las alertas de tráfico con ubicación textual.
 */
public record TomTomReverseGeocodeResponse(
    @JsonProperty("addresses") List<Address> addresses
) {
    public record Address(
        @JsonProperty("address") AddressDetails address
    ) {}

    public record AddressDetails(
        @JsonProperty("streetName") String streetName,
        @JsonProperty("municipality") String municipality,
        @JsonProperty("neighbourhood") String neighbourhood,
        @JsonProperty("countrySubdivision") String countrySubdivision,
        @JsonProperty("countryCode") String countryCode,
        @JsonProperty("postalCode") String postalCode,
        @JsonProperty("freeformAddress") String freeformAddress,
        @JsonProperty("localName") String localName
    ) {}
}
