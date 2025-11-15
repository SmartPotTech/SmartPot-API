package app.smartpot.api.actuators.service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import app.smartpot.api.actuators.mapper.ActuatorMapper;
import app.smartpot.api.actuators.model.dto.ActuatorDTO;
import app.smartpot.api.actuators.model.entity.Actuator;
import app.smartpot.api.actuators.repository.ActuatorRepository;
import smartpot.com.api.Crops.Service.SCropI;
import smartpot.com.api.Exception.ApiException;
import smartpot.com.api.Exception.ApiResponse;

import java.util.List;

@Slf4j
@Data
@Builder
@Service
public class ServiceActuatorImpl implements ServiceActuator {

    private final ActuatorRepository actuatorRepository;
    private final SCropI serviceCrop;

    @Autowired
    public ServiceActuatorImpl(ActuatorRepository actuatorRepository, SCropI serviceCrop) {
        this.actuatorRepository = actuatorRepository;
        this.serviceCrop = serviceCrop;
    }

    @Override
    public List<Actuator> getAllActuators() {
        List<Actuator> actuators = actuatorRepository.findAll();
        if (actuators.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "No se encontró ningún actuador en el sistema",
                    HttpStatus.NOT_FOUND.value()
            ));
        }
        return actuators;
    }

    @Override
    public Actuator getActuatorById(String id) {
        if (!ObjectId.isValid(id)) {
            throw new ApiException(new ApiResponse(
                    "El id '" + id + "' no es válido. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
        return actuatorRepository.findById(new ObjectId(id))
                .orElseThrow(() -> new ApiException(
                        new ApiResponse("El Actuador con id '" + id + "' no fue encontrado.",
                                HttpStatus.NOT_FOUND.value())
                ));
    }

    @Override
    public List<Actuator> getActuatorsByCrop(String crop) {
        if (!ObjectId.isValid(crop)) {
            throw new ApiException(new ApiResponse(
                    "El id de cultivo '" + crop + "' no es válido. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
        return actuatorRepository.findByCrop(new ObjectId(crop));
    }

    @Override
    public Actuator createActuator(ActuatorDTO actuator) throws Exception {
        serviceCrop.getCropById(actuator.getCrop());
        Actuator act = ActuatorMapper.INSTANCE.toEntity(actuator);
        return actuatorRepository.save(act);
    }

    @Override
    public Actuator updateActuator(Actuator existingActuator, ActuatorDTO actuator) {
        if (actuator.getId() != null && actuator.getCrop() != null) {
            existingActuator = ActuatorMapper.INSTANCE.toEntity(actuator);
        }

        try {
            return actuatorRepository.save(existingActuator);
        } catch (Exception e) {
            log.error("e: ", e);
            throw new ApiException(
                    new ApiResponse("No se pudo actualizar el actuador con ID '" + existingActuator.getId() + "'.",
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @Override
    public ResponseEntity<ApiResponse> deleteActuatorById(Actuator actuator) {
        try {
            actuatorRepository.deleteById(actuator.getId());
            return ResponseEntity.status(HttpStatus.OK.value()).body(
                    new ApiResponse("El Actuador con ID '" + actuator.getId() + "' fue eliminado.",
                            HttpStatus.OK.value())
            );
        } catch (Exception e) {
            log.error("e: ", e);
            throw new ApiException(
                    new ApiResponse("No se pudo eliminar el Actuador con ID '" + actuator.getId() + "'.",
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    //@Override
    //public ResponseEntity<ApiResponse> deleteActuators(List<Actuadores> ids) {
    //    return null;
    //}
}
