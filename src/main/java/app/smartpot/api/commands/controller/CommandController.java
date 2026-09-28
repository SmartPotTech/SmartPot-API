package app.smartpot.api.commands.controller;

import app.smartpot.api.commands.mapper.CommandMapper;
import app.smartpot.api.commands.model.dto.CommandRequest;
import app.smartpot.api.commands.model.dto.CommandResponse;
import app.smartpot.api.commands.service.CommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/crops/{cropId}/commands")
@Tag(name = "Comandos", description = "Órdenes a los actuadores, enviadas por MQTT y confirmadas por el dispositivo")
public class CommandController {

    private final CommandService commandService;

    public CommandController(CommandService commandService) {
        this.commandService = commandService;
    }

    @GetMapping
    @Operation(summary = "Historial de comandos", description = "Más recientes primero; máximo 100")
    public List<CommandResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId,
                                      @RequestParam(defaultValue = "30") int limit) {
        return commandService.list(jwt.getSubject(), cropId, limit).stream().map(CommandMapper::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Enviar un comando",
            description = "Responde con el estado SENT; la confirmación del dispositivo llega después (EXECUTED o FAILED)")
    public CommandResponse send(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId,
                                @Valid @RequestBody CommandRequest request) {
        return CommandMapper.toResponse(commandService.request(jwt.getSubject(), cropId, request));
    }
}
