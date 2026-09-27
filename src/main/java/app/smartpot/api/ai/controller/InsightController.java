package app.smartpot.api.ai.controller;

import app.smartpot.api.ai.model.dto.InsightResponse;
import app.smartpot.api.ai.service.AiClient;
import app.smartpot.api.ai.service.InsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@Tag(name = "Asistente de IA", description = "Diagnóstico, salud y recomendaciones de cada cultivo")
public class InsightController {

    private final InsightService insightService;
    private final AiClient aiClient;

    public InsightController(InsightService insightService, AiClient aiClient) {
        this.insightService = insightService;
        this.aiClient = aiClient;
    }

    @GetMapping("/crops/{cropId}/insights")
    @Operation(summary = "Evaluar el cultivo con la IA",
            description = "Sistema experto, índice difuso de salud, modelos de ML y acciones sugeridas sobre la última lectura")
    public InsightResponse insights(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId) {
        return insightService.forOwner(jwt.getSubject(), cropId);
    }

    @GetMapping(value = "/ai/learning", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Aprendizaje continuo de la IA",
            description = "Lecturas reales por especie, calidad de los datos, comparación de modelos supervisados, "
                    + "estados de operación y detector de atípicos. Solo datos agregados: sin cultivos ni personas.")
    public ResponseEntity<String> learning() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePrivate())
                .body(aiClient.learningStatusJson());
    }

    @GetMapping(value = "/crop-profiles", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Perfiles de cultivo", description = "Rangos óptimos por especie de la base de conocimiento (público)")
    public ResponseEntity<String> cropProfiles() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(aiClient.cropProfilesJson());
    }
}
