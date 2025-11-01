package smartpot.com.api.Actuators.Service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import smartpot.com.api.Actuators.Model.DTO.ActuatorDTO;
import smartpot.com.api.Actuators.Model.Entity.Actuator;
import smartpot.com.api.Actuators.Repository.RActuator;
import smartpot.com.api.Exception.ApiException;
import smartpot.com.api.Exception.ApiResponse;

import java.util.List;

@Slf4j
@Data
@Builder
@Service
public class SActuator implements SActuatorI {

    private final RActuator actuatorRepository;

    @Autowired
    public SActuator(RActuator actuatorRepository) {
        this.actuatorRepository = actuatorRepository;
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
    public Actuator CreateActuator(ActuatorDTO actuator) {
        //Actuator act = new Actuator();
        //return actuatorRepository.save(actuator);
        return null;
    }

    @Override
    public Actuator UpdateActuator(ActuatorDTO actuator) {
        return null;
    }

    @Override
    public Actuator DeleteActuatorById(String id) {
        return null;
    }

    @Override
    public ResponseEntity<ApiResponse> DeleteActuators(List<String> ids) {
        return null;
    }
}
