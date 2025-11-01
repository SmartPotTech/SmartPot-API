package smartpot.com.api.Actuators.Service;

import org.springframework.http.ResponseEntity;
import smartpot.com.api.Actuators.Model.DTO.ActuatorDTO;
import smartpot.com.api.Actuators.Model.Entity.Actuator;
import smartpot.com.api.Exception.ApiResponse;

import java.util.List;

public interface SActuatorI {
    List<Actuator> getAllActuators();

    Actuator getActuatorById(String id);

    List<Actuator> getActuatorsByCrop(String crop);

    Actuator createActuator(ActuatorDTO actuator) throws Exception;

    Actuator updateActuator(Actuator existingActuator , ActuatorDTO actuator);

    ResponseEntity<ApiResponse> deleteActuatorById(Actuator actuator);

    //ResponseEntity<ApiResponse> deleteActuators(List<String> ids);
}
