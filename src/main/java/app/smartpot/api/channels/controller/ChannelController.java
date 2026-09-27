package app.smartpot.api.channels.controller;

import app.smartpot.api.channels.model.dto.ChannelLinkResponse;
import app.smartpot.api.channels.model.dto.ChannelLinkUpdateRequest;
import app.smartpot.api.channels.model.dto.ChannelOptionResponse;
import app.smartpot.api.channels.model.dto.LinkCodeResponse;
import app.smartpot.api.channels.model.entity.ChannelType;
import app.smartpot.api.channels.service.ChannelService;
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
@RequestMapping("/channels")
@Tag(name = "Canales", description = "Notificaciones fuera de la app: hoy Telegram")
public class ChannelController {

    private final ChannelService channelService;

    public ChannelController(ChannelService channelService) {
        this.channelService = channelService;
    }

    @GetMapping
    @Operation(summary = "Canales disponibles y mis vínculos")
    public List<ChannelOptionResponse> overview(@AuthenticationPrincipal Jwt jwt) {
        return channelService.overview(jwt.getSubject());
    }

    @PostMapping("/{type}/link")
    @Operation(summary = "Crear un código de vinculación",
            description = "Código de un solo uso, válido 10 minutos; la url abre el canal con el código")
    public LinkCodeResponse link(@AuthenticationPrincipal Jwt jwt, @PathVariable String type) {
        return channelService.createLinkCode(jwt.getSubject(), parse(type));
    }

    @PutMapping("/links/{id}")
    @Operation(summary = "Activar, pausar o elegir qué notificaciones recibir")
    public ChannelLinkResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                                      @Valid @RequestBody ChannelLinkUpdateRequest request) {
        return channelService.update(jwt.getSubject(), id, request);
    }

    @PostMapping("/links/{id}/test")
    @Operation(summary = "Enviar un mensaje de prueba")
    public ResponseEntity<Void> test(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        channelService.sendTest(jwt.getSubject(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/links/{id}")
    @Operation(summary = "Desvincular")
    public ResponseEntity<Void> unlink(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        channelService.unlink(jwt.getSubject(), id);
        return ResponseEntity.noContent().build();
    }

    private static ChannelType parse(String type) {
        return Arrays.stream(ChannelType.values())
                .filter(value -> value.name().equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("El canal no existe"));
    }
}
