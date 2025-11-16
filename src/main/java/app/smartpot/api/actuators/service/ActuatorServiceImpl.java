package app.smartpot.api.actuators.service;

import jakarta.validation.ValidationException;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import app.smartpot.api.actuators.mapper.ActuatorMapper;
import app.smartpot.api.actuators.model.dto.ActuatorDTO;
import app.smartpot.api.actuators.repository.ActuatorRepository;
import app.smartpot.api.crops.service.CropService;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Data
@Builder
@Service
public class ActuatorServiceImpl implements ActuatorService {

    private final ActuatorRepository actuatorRepository;
    private final CropService serviceCrop;
    private final ActuatorMapper actuatorMapper;

    @Autowired
    public ActuatorServiceImpl(ActuatorRepository actuatorRepository, CropService serviceCrop, ActuatorMapper actuatorMapper) {
        this.actuatorRepository = actuatorRepository;
        this.serviceCrop = serviceCrop;
        this.actuatorMapper = actuatorMapper;
    }

    @Override
    public List<ActuatorDTO> getAllActuators() throws Exception {
        return Optional.of(actuatorRepository.findAll())
                .filter(actuators -> !actuators.isEmpty())
                .map(actuators -> actuators.stream()
                        .map(actuatorMapper::toDTO)
                        .collect(Collectors.toList()))
                .orElseThrow(() -> new Exception("No existe ningún actuador"));
    }

    @Override
    public ActuatorDTO getActuatorById(String id) throws Exception {
        return Optional.of(id)
                .map(ValidId -> {
                    if (!ObjectId.isValid(id)) {
                        throw new ValidationException("Id invalido");
                    }
                    return new ObjectId(ValidId);
                })
                .map(actuatorRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(actuatorMapper::toDTO)
                .orElseThrow(() -> new Exception("El Actuador no existe"));
    }

    @Override
    public List<ActuatorDTO> getActuatorsByCrop(String crop) throws Exception {
        return Optional.of(crop)
                .map(id -> {
                    if (!ObjectId.isValid(id)) {
                        throw new ValidationException("Id de crop invalido");
                    }
                    return new ObjectId(id);
                })
                .map(actuatorRepository::findByCrop)
                .filter(users -> !users.isEmpty())
                .map(actuators -> actuators.stream()
                        .map(actuatorMapper::toDTO)
                        .collect(Collectors.toList())
                )
                .orElseThrow(() -> new Exception("El actuador no existe"));
    }

    @Override
    public ActuatorDTO createActuator(ActuatorDTO actuator) throws Exception {
        return Optional.of(actuator)
                 .map(actuatorMapper::toEntity)
                .map(actuatorRepository::save)
                .map(actuatorMapper::toDTO)
                .orElseThrow(() -> new IllegalStateException("El Usuario ya existe"));
    }

    @Override
    public ActuatorDTO updateActuator(String id, ActuatorDTO actuator) throws Exception {
        ActuatorDTO existingActuator = getActuatorById(id);
        log.debug("UPDATING......................");
        return Optional.of(actuator)
                .map(updated -> {
                    existingActuator.setType(updated.getType());
                    return existingActuator;
                })
                .map(actuatorMapper::toEntity)
                .map(actuatorRepository::save)
                .map(actuatorMapper::toDTO)
                .orElseThrow(() -> new Exception("El usuario no se pudo actualizar"));
    }

    @Override
    public String deleteActuatorById(String id) throws Exception {
        return Optional.of(getActuatorById(id))
                .map(user -> {
                    actuatorRepository.deleteById(new ObjectId(user.getId()));
                    return "El Usuario con ID '" + id + "' fue eliminado.";
                })
                .orElseThrow(() -> new Exception("El Usuario no existe."));
    }

}
