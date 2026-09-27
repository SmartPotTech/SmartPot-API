package app.smartpot.api.readings.controller;

import app.smartpot.api.exception.ApiException;
import app.smartpot.api.readings.mapper.ReadingMapper;
import app.smartpot.api.readings.model.dto.MeasuresRequest;
import app.smartpot.api.readings.model.dto.ReadingResponse;
import app.smartpot.api.readings.model.dto.ReadingSummaryResponse;
import app.smartpot.api.readings.service.ReadingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/crops/{cropId}/readings")
@Tag(name = "Lecturas", description = "Telemetría de los sensores de cada cultivo")
public class ReadingController {

    private final ReadingService readingService;

    public ReadingController(ReadingService readingService) {
        this.readingService = readingService;
    }

    @GetMapping
    @Operation(summary = "Listar lecturas", description = "Orden cronológico; por defecto las últimas 24 horas")
    public List<ReadingResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                      @RequestParam(required = false) Integer limit) {
        return readingService.list(jwt.getSubject(), cropId, from, to, limit).stream()
                .map(ReadingMapper::toResponse)
                .toList();
    }

    @GetMapping("/latest")
    @Operation(summary = "Última lectura")
    public ReadingResponse latest(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId) {
        return readingService.latestOwned(jwt.getSubject(), cropId)
                .map(ReadingMapper::toResponse)
                .orElseThrow(() -> ApiException.notFound("El cultivo todavía no tiene lecturas"));
    }

    @GetMapping("/summary")
    @Operation(summary = "Resumen estadístico", description = "Mínimo, promedio y máximo de cada variable")
    public ReadingSummaryResponse summary(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId,
                                         @RequestParam(defaultValue = "24") int hours) {
        return readingService.summary(jwt.getSubject(), cropId, hours);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Exportar lecturas a CSV", description = "Hasta 50 000 filas del rango pedido")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        byte[] csv = readingService.exportCsv(jwt.getSubject(), cropId, from, to).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("smartpot-" + cropId + ".csv").build().toString())
                .body(csv);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una lectura manual", description = "Para dispositivos sin MQTT o pruebas; se prefiere MQTT")
    public ReadingResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId,
                                  @RequestBody MeasuresRequest request) {
        return ReadingMapper.toResponse(readingService.recordManual(jwt.getSubject(), cropId, request.toMeasures()));
    }
}
