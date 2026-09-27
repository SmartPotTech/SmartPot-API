package app.smartpot.api.crops.controller;

import app.smartpot.api.crops.mapper.CropMapper;
import app.smartpot.api.crops.model.dto.AutomationRequest;
import app.smartpot.api.crops.model.dto.BulkAutomationRequest;
import app.smartpot.api.crops.model.dto.CropCreatedResponse;
import app.smartpot.api.crops.model.dto.CropRequest;
import app.smartpot.api.crops.model.dto.CropResponse;
import app.smartpot.api.crops.model.dto.DeviceCredentialsResponse;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.model.entity.CropKind;
import app.smartpot.api.crops.service.CropService;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.readings.service.ReadingService;
import app.smartpot.api.virtualdevices.service.VirtualDeviceService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/crops")
@Tag(name = "Cultivos", description = "Cultivos reales y virtuales del usuario, su automatización y las credenciales del dispositivo")
public class CropController {

    private final CropService cropService;
    private final ReadingService readingService;
    private final VirtualDeviceService virtualDevices;

    public CropController(CropService cropService, ReadingService readingService, VirtualDeviceService virtualDevices) {
        this.cropService = cropService;
        this.readingService = readingService;
        this.virtualDevices = virtualDevices;
    }

    @GetMapping
    @Operation(summary = "Listar mis cultivos", description = "Incluye la última lectura y la última evaluación de salud")
    public List<CropResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return cropService.list(jwt.getSubject()).stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un cultivo real o virtual",
            description = "El tipo (REAL o VIRTUAL) no se puede cambiar después. Un cultivo real devuelve una única vez "
                    + "la clave de su dispositivo; uno virtual arranca su simulación y no expone credenciales")
    public CropCreatedResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CropRequest request) {
        boolean virtual = request.kind() == CropKind.VIRTUAL;
        if (virtual) {
            virtualDevices.checkCanCreate(jwt.getSubject(), request.virtual());
        } else if (request.virtual() != null) {
            throw ApiException.badRequest("Solo los cultivos virtuales tienen simulación");
        }
        CropService.CreatedCrop created = cropService.create(jwt.getSubject(), request);
        if (virtual) {
            virtualDevices.startFor(created.crop(), request.virtual());
        }
        return new CropCreatedResponse(CropMapper.toResponse(created.crop(), null), created.credentials());
    }

    @GetMapping("/{cropId}")
    @Operation(summary = "Obtener un cultivo")
    public CropResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId) {
        return toResponse(cropService.getOwned(jwt.getSubject(), cropId));
    }

    @PutMapping("/{cropId}")
    @Operation(summary = "Cambiar nombre, especie o forma", description = "Real o virtual no se puede cambiar")
    public CropResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId,
                               @Valid @RequestBody CropRequest request) {
        return toResponse(cropService.update(jwt.getSubject(), cropId, request));
    }

    @DeleteMapping("/{cropId}")
    @Operation(summary = "Eliminar un cultivo", description = "Borra también lecturas, comandos, actuadores y la cuenta MQTT")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId) {
        cropService.delete(jwt.getSubject(), cropId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/automation")
    @Operation(summary = "Modo automático en varios cultivos",
            description = "Sin cropIds se aplica a todos los cultivos de la cuenta")
    public List<CropResponse> bulkAutomation(@AuthenticationPrincipal Jwt jwt,
                                             @Valid @RequestBody BulkAutomationRequest request) {
        return cropService.setAutomation(jwt.getSubject(), request.cropIds(), request.enabled()).stream()
                .map(this::toResponse).toList();
    }

    @PutMapping("/{cropId}/automation")
    @Operation(summary = "Activar o desactivar el modo automático",
            description = "Con el modo automático, el agente de IA ejecuta sus recomendaciones sobre los actuadores")
    public CropResponse automation(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId,
                                   @Valid @RequestBody AutomationRequest request) {
        return toResponse(cropService.setAutomation(jwt.getSubject(), cropId, request.enabled()));
    }

    @GetMapping("/{cropId}/device")
    @Operation(summary = "Datos de conexión del dispositivo",
            description = "Broker, puerto, usuario y tópicos, sin la clave. Solo cultivos reales")
    public DeviceCredentialsResponse device(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId) {
        return cropService.deviceInfo(jwt.getSubject(), cropId);
    }

    @PostMapping("/{cropId}/device/key")
    @Operation(summary = "Generar una nueva clave del dispositivo",
            description = "La clave anterior deja de funcionar y el dispositivo conectado se desconecta")
    public DeviceCredentialsResponse rotateKey(@AuthenticationPrincipal Jwt jwt, @PathVariable String cropId) {
        return cropService.rotateDeviceKey(jwt.getSubject(), cropId);
    }

    private CropResponse toResponse(Crop crop) {
        return CropMapper.toResponse(crop, readingService.latest(crop.getId()).orElse(null));
    }
}
