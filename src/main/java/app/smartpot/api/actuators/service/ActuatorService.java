package app.smartpot.api.actuators.service;

import app.smartpot.api.actuators.model.entity.Actuator;
import app.smartpot.api.actuators.model.entity.ActuatorType;
import app.smartpot.api.actuators.repository.ActuatorRepository;
import app.smartpot.api.crops.service.CropService;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.exception.ObjectIds;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ActuatorService {

    private static final String NOT_FOUND = "El actuador no existe en este cultivo";

    private final ActuatorRepository repository;
    private final CropService cropService;
    private final Clock clock;

    public ActuatorService(ActuatorRepository repository, CropService cropService, Clock clock) {
        this.repository = repository;
        this.cropService = cropService;
        this.clock = clock;
    }

    public List<Actuator> list(String ownerId, String cropId) {
        cropService.getOwned(ownerId, cropId);
        return listForCrop(cropId);
    }

    public List<Actuator> listForCrop(String cropId) {
        return repository.findByCropIdOrderByCreatedAtAsc(cropId);
    }

    public Actuator add(String ownerId, String cropId, ActuatorType type) {
        cropService.getOwned(ownerId, cropId);
        if (repository.existsByCropIdAndType(cropId, type)) {
            throw ApiException.conflict("El cultivo ya tiene un actuador de ese tipo");
        }
        return repository.save(Actuator.builder()
                .cropId(cropId)
                .type(type)
                .active(false)
                .createdAt(clock.instant())
                .build());
    }

    public void remove(String ownerId, String cropId, String actuatorId) {
        cropService.getOwned(ownerId, cropId);
        repository.delete(getForCrop(cropId, actuatorId));
    }

    public Actuator getForCrop(String cropId, String actuatorId) {
        ObjectIds.require(actuatorId, NOT_FOUND);
        return repository.findByIdAndCropId(actuatorId, cropId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    public Optional<Actuator> findByType(String cropId, ActuatorType type) {
        return repository.findByCropIdAndType(cropId, type);
    }

    /** Estado confirmado: encendido sin límite (active), encendido hasta una hora (runningUntil) o apagado. */
    public void updateState(String actuatorId, boolean active, Instant runningUntil) {
        repository.findById(actuatorId).ifPresent(actuator -> {
            actuator.setActive(active);
            actuator.setRunningUntil(runningUntil);
            actuator.setLastChangedAt(clock.instant());
            repository.save(actuator);
        });
    }
}
