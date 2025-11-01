package smartpot.com.api.Actuators.Service;

import org.springframework.http.ResponseEntity;
import smartpot.com.api.Actuators.Model.Entity.Actuator;
import smartpot.com.api.Exception.ApiResponse;

import java.util.List;

public interface SActuatorI {
    List<Actuator> getAllActuators();

    Actuator getActuatorById(String id);

    List<Actuator> getActuatorsByCrop(String crop);

    Actuator CreateActuator(Actuator actuator);

    Actuator UpdateActuator(Actuator actuator);

    Actuator DeleteActuatorById(String id);

    ResponseEntity<ApiResponse> DeleteActuators(List<String> ids);
}
