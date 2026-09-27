package app.smartpot.api.ai.service;

import app.smartpot.api.ai.config.AiProperties;
import app.smartpot.api.ai.model.dto.FleetRequest;
import app.smartpot.api.ai.model.dto.FleetResponse;
import app.smartpot.api.ai.model.dto.InsightRequest;
import app.smartpot.api.ai.model.dto.InsightResponse;
import app.smartpot.api.cache.CacheStore;
import app.smartpot.api.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class AiClient {

    private static final String UNAVAILABLE = "El asistente de IA no está disponible en este momento";
    private static final Duration PROFILES_TTL = Duration.ofHours(1);
    private static final Duration HEALTH_TTL = Duration.ofSeconds(30);

    private final AiProperties properties;
    private final CacheStore cacheStore;
    private final Clock clock;
    private final RestClient restClient;
    private volatile Instant healthCheckedAt = Instant.EPOCH;
    private volatile boolean healthy;

    public AiClient(AiProperties properties, CacheStore cacheStore, Clock clock, RestClient.Builder builder) {
        this.properties = properties;
        this.cacheStore = cacheStore;
        this.clock = clock;
        // HTTP/1.1 explícito: uvicorn rechaza el intento de actualización a h2c del cliente de Java.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.timeout());
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
                .build();
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    public InsightResponse insights(InsightRequest request) {
        requireEnabled();
        try {
            InsightResponse response = restClient.post()
                    .uri("/v1/insights")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(InsightResponse.class);
            if (response == null) {
                throw ApiException.unavailable(UNAVAILABLE);
            }
            return response;
        } catch (RestClientException ex) {
            log.warn("Falló la consulta al servicio de IA: {}", ex.getMessage());
            throw ApiException.unavailable(UNAVAILABLE);
        }
    }

    /** Análisis de todos los cultivos de una cuenta: ranking, problemas compartidos, grupos y acciones en bloque. */
    public FleetResponse fleet(FleetRequest request) {
        requireEnabled();
        try {
            FleetResponse response = restClient.post()
                    .uri("/v1/fleet")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(FleetResponse.class);
            if (response == null) {
                throw ApiException.unavailable(UNAVAILABLE);
            }
            return response;
        } catch (RestClientException ex) {
            log.warn("Falló el análisis de flota del servicio de IA: {}", ex.getMessage());
            throw ApiException.unavailable(UNAVAILABLE);
        }
    }

    /** Perfiles de cultivo (rangos óptimos). Se guardan una hora en caché porque casi nunca cambian. */
    public String cropProfilesJson() {
        requireEnabled();
        return cacheStore.get("ai:crop-profiles").orElseGet(() -> {
            try {
                String body = restClient.get().uri("/v1/crop-profiles").retrieve().body(String.class);
                if (body == null) {
                    throw ApiException.unavailable(UNAVAILABLE);
                }
                cacheStore.put("ai:crop-profiles", body, PROFILES_TTL);
                return body;
            } catch (RestClientException ex) {
                log.warn("No se pudieron leer los perfiles de cultivo: {}", ex.getMessage());
                throw ApiException.unavailable(UNAVAILABLE);
            }
        });
    }

    public boolean isHealthy() {
        if (!properties.enabled()) {
            return false;
        }
        Instant now = clock.instant();
        if (now.isBefore(healthCheckedAt.plus(HEALTH_TTL))) {
            return healthy;
        }
        try {
            restClient.get().uri("/health").retrieve().toBodilessEntity();
            healthy = true;
        } catch (RestClientException ex) {
            healthy = false;
        }
        healthCheckedAt = now;
        return healthy;
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw ApiException.unavailable("El asistente de IA está deshabilitado en este servidor");
        }
    }
}
