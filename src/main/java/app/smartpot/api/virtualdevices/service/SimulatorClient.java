package app.smartpot.api.virtualdevices.service;

import app.smartpot.api.exception.ApiException;
import app.smartpot.api.virtualdevices.config.SimulatorProperties;
import app.smartpot.api.virtualdevices.model.dto.PlaceResponse;
import app.smartpot.api.virtualdevices.model.dto.SimulatorPot;
import app.smartpot.api.virtualdevices.model.dto.SimulatorPotRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;

/** Cliente de la API interna del simulador de dispositivos. */
@Slf4j
@Component
public class SimulatorClient {

    static final String UNAVAILABLE = "El simulador de cultivos virtuales no está disponible en este momento";

    private final SimulatorProperties properties;
    private final RestClient restClient;

    public SimulatorClient(SimulatorProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        // HTTP/1.1 explícito, igual que con la IA: uvicorn no acepta la actualización a h2c.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.timeout());
        this.restClient = builder.clone()
                .baseUrl(properties.baseUrl() == null ? "http://localhost:8081" : properties.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
                .build();
    }

    public boolean isAvailable() {
        return properties.available();
    }

    public SimulatorPot put(String cropId, SimulatorPotRequest request) {
        requireAvailable();
        try {
            SimulatorPot pot = restClient.put()
                    .uri("/v1/pots/{id}", cropId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(SimulatorPot.class);
            if (pot == null) {
                throw ApiException.unavailable(UNAVAILABLE);
            }
            return pot;
        } catch (HttpClientErrorException ex) {
            throw ApiException.badRequest("El simulador rechazó la configuración: " + ex.getStatusText());
        } catch (RestClientException ex) {
            log.warn("Falló la simulación del cultivo virtual {}: {}", cropId, ex.getMessage());
            throw ApiException.unavailable(UNAVAILABLE);
        }
    }

    /** Estado en vivo; vacío si el cultivo no existe en el simulador o si este no responde. */
    public Optional<SimulatorPot> get(String cropId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(restClient.get().uri("/v1/pots/{id}", cropId).retrieve()
                    .body(SimulatorPot.class));
        } catch (RestClientException ex) {
            return Optional.empty();
        }
    }

    public List<SimulatorPot> list() {
        requireAvailable();
        try {
            List<SimulatorPot> pots = restClient.get().uri("/v1/pots").retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return pots == null ? List.of() : pots;
        } catch (RestClientException ex) {
            throw ApiException.unavailable(UNAVAILABLE);
        }
    }

    public void delete(String cropId) {
        if (!isAvailable()) {
            return;
        }
        try {
            restClient.delete().uri("/v1/pots/{id}", cropId).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            if (!ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                log.warn("No se pudo retirar la simulación del cultivo {}: {}", cropId, ex.getStatusText());
            }
        } catch (RestClientException ex) {
            log.warn("No se pudo retirar la simulación del cultivo {}: {}", cropId, ex.getMessage());
        }
    }

    public List<PlaceResponse> places(String query) {
        requireAvailable();
        try {
            List<PlaceResponse> places = restClient.get()
                    .uri(uri -> uri.path("/v1/places").queryParam("q", query).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return places == null ? List.of() : places;
        } catch (RestClientException ex) {
            throw ApiException.unavailable("No se pudo consultar el buscador de lugares");
        }
    }

    public boolean isHealthy() {
        if (!isAvailable()) {
            return false;
        }
        try {
            restClient.get().uri("/health").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException ex) {
            return false;
        }
    }

    private void requireAvailable() {
        if (!isAvailable()) {
            throw ApiException.unavailable("Los cultivos virtuales no están habilitados en este servidor");
        }
    }
}
