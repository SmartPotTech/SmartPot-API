package app.smartpot.api.virtualdevices.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlaceResponse(String name, double latitude, double longitude, String country, String region) {
}
