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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Maceta virtual", description = "Simulador siempre encendido: manual o con el clima real del lugar")
public class VirtualDeviceController {

    private final VirtualDeviceService service;

    public VirtualDeviceController(VirtualDeviceService service) {
        this.service = service;
    }

    @GetMapping("/crops/{cropId}/virtual-device")
    @Operation(summary = "Estado de la maceta virtual del cultivo")
    public VirtualDeviceResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId) {
        return service.get(jwt.getSubject(), cropId);
    }

    @PutMapping("/crops/{cropId}/virtual-device")
    @Operation(summary = "Encender o cambiar la maceta virtual",
            description = "Modo WEATHER con una ubicación, MANUAL con los medidores o AUTO (día y noche típicos)")
    public VirtualDeviceResponse configure(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId,
                                           @Valid @RequestBody VirtualDeviceRequest request) {
        return service.configure(jwt.getSubject(), cropId, request);
    }

    @DeleteMapping("/crops/{cropId}/virtual-device")
    @Operation(summary = "Apagar la maceta virtual")
    public ResponseEntity<Void> stop(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId) {
        service.stop(jwt.getSubject(), cropId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/virtual-devices/places")
    @Operation(summary = "Buscar un lugar para el modo clima")
    public List<PlaceResponse> places(@RequestParam("q") String query) {
        return service.places(query);
    }
}
