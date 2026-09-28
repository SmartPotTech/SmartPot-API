package app.smartpot.api.virtualdevices.controller;

import app.smartpot.api.virtualdevices.model.dto.PlaceResponse;
import app.smartpot.api.virtualdevices.model.dto.VirtualDeviceRequest;
import app.smartpot.api.virtualdevices.model.dto.VirtualDeviceResponse;
import app.smartpot.api.virtualdevices.service.VirtualDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Cultivo virtual", description = "Simulación siempre encendida: manual, día y noche o con el clima real del lugar")
public class VirtualDeviceController {

    private final VirtualDeviceService service;

    public VirtualDeviceController(VirtualDeviceService service) {
        this.service = service;
    }

    @GetMapping("/crops/{cropId}/virtual-device")
    @Operation(summary = "Estado de la simulación de un cultivo virtual")
    public VirtualDeviceResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId) {
        return service.get(jwt.getSubject(), cropId);
    }

    @PutMapping("/crops/{cropId}/virtual-device")
    @Operation(summary = "Cambiar o reanudar la simulación de un cultivo virtual",
            description = "Modo WEATHER con una ubicación, MANUAL con los medidores o AUTO (día y noche típicos). "
                    + "Los cultivos reales responden 400")
    public VirtualDeviceResponse configure(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId,
                                           @Valid @RequestBody VirtualDeviceRequest request) {
        return service.configure(jwt.getSubject(), cropId, request);
    }

    @DeleteMapping("/crops/{cropId}/virtual-device")
    @Operation(summary = "Pausar la simulación", description = "Deja de publicar lecturas y conserva la configuración")
    public ResponseEntity<Void> pause(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId) {
        service.pause(jwt.getSubject(), cropId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/virtual-devices/places")
    @Operation(summary = "Buscar un lugar para el modo clima")
    public List<PlaceResponse> places(@RequestParam("q") String query) {
        return service.places(query);
    }
}
