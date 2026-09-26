package app.smartpot.api.actuators.controller;

import app.smartpot.api.actuators.mapper.ActuatorMapper;
import app.smartpot.api.actuators.model.dto.ActuatorRequest;
import app.smartpot.api.actuators.model.dto.ActuatorResponse;
import app.smartpot.api.actuators.service.ActuatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/crops/{cropId}/actuators")
@Tag(name = "Actuadores", description = "Bomba, luz UV, ventilador y demás salidas de la maceta")
public class ActuatorController {

    private final ActuatorService actuatorService;

    public ActuatorController(ActuatorService actuatorService) {
        this.actuatorService = actuatorService;
    }

    @GetMapping
    @Operation(summary = "Listar actuadores del cultivo")
    public List<ActuatorResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId) {
        return actuatorService.list(jwt.getSubject(), cropId).stream().map(ActuatorMapper::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agregar un actuador", description = "Un actuador por tipo en cada cultivo")
    public ActuatorResponse add(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId,
                                @Valid @RequestBody ActuatorRequest request) {
        return ActuatorMapper.toResponse(actuatorService.add(jwt.getSubject(), cropId, request.type()));
    }

    @DeleteMapping("/{actuatorId}")
    @Operation(summary = "Quitar un actuador")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId,
                                       @PathVariable String actuatorId) {
        actuatorService.remove(jwt.getSubject(), cropId, actuatorId);
        return ResponseEntity.noContent().build();
    }
}
