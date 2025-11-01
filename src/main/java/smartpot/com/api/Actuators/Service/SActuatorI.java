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

    Actuator CreateActuator(ActuatorDTO actuator);

    Actuator UpdateActuator(ActuatorDTO actuator);

    Actuator DeleteActuatorById(String id);

    ResponseEntity<ApiResponse> DeleteActuators(List<String> ids);
}
