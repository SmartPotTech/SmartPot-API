package app.smartpot.api.commands.controller;

import app.smartpot.api.commands.mapper.CommandMapper;
import app.smartpot.api.commands.model.dto.BulkCommandRequest;
import app.smartpot.api.commands.model.dto.BulkCommandResponse;
import app.smartpot.api.commands.model.dto.CommandResponse;
import app.smartpot.api.commands.service.CommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/commands")
@Tag(name = "Centro de acciones", description = "Historial de todos los cultivos y órdenes en bloque")
public class CommandCenterController {

    private final CommandService commandService;

    public CommandCenterController(CommandService commandService) {
        this.commandService = commandService;
    }

    @GetMapping
    @Operation(summary = "Comandos de todos mis cultivos", description = "Más recientes primero; máximo 200")
    public List<CommandResponse> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "50") int limit) {
        return commandService.listForOwner(jwt.getSubject(), limit).stream().map(CommandMapper::toResponse).toList();
    }

    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Enviar la misma orden a varios cultivos",
            description = "Sin cropIds se aplica a todos. Cada cultivo responde SENT, FAILED o SKIPPED con su motivo")
    public BulkCommandResponse bulk(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BulkCommandRequest request) {
        return commandService.requestBulk(jwt.getSubject(), request);
    }
}
