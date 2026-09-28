package app.smartpot.api.channels.controller;

import app.smartpot.api.channels.model.dto.CropChannelResponse;
import app.smartpot.api.channels.model.dto.CropChannelUpdateRequest;
import app.smartpot.api.channels.model.dto.LinkCodeResponse;
import app.smartpot.api.channels.model.entity.ChannelType;
import app.smartpot.api.channels.service.CropChannelService;
import app.smartpot.api.exception.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/crops/{cropId}/channels")
@Tag(name = "Canales", description = "Notificaciones fuera de la app: hoy Telegram")
public class CropChannelController {

    private final CropChannelService service;

    public CropChannelController(CropChannelService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Avisos del cultivo por cada canal")
    public List<CropChannelResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId) {
        return service.list(jwt.getSubject(), cropId);
    }

    @PutMapping("/{type}")
    @Operation(summary = "Elegir qué avisa el cultivo, cómo y cuándo llega el resumen diario")
    public CropChannelResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId,
                                      @PathVariable String type, @Valid @RequestBody CropChannelUpdateRequest request) {
        return service.update(jwt.getSubject(), cropId, parse(type), request);
    }

    @PostMapping("/{type}/recipients")
    @Operation(summary = "Crear un enlace para compartir los avisos con otro chat",
            description = "Enlace de un solo uso, válido 10 minutos")
    public LinkCodeResponse share(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId,
                                  @PathVariable String type) {
        return service.shareCode(jwt.getSubject(), cropId, parse(type));
    }

    @DeleteMapping("/{type}/recipients/{recipientId}")
    @Operation(summary = "Dejar de compartir los avisos con un chat")
    public ResponseEntity<Void> removeRecipient(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId,
                                                @PathVariable String type, @PathVariable String recipientId) {
        service.removeRecipient(jwt.getSubject(), cropId, parse(type), recipientId);
        return ResponseEntity.noContent().build();
    }

    private static ChannelType parse(String type) {
        return Arrays.stream(ChannelType.values())
                .filter(value -> value.name().equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("El canal no existe"));
    }
}
