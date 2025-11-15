package app.smartpot.api.actuators.service;

import org.springframework.http.ResponseEntity;
import app.smartpot.api.actuators.model.dto.ActuatorDTO;
import app.smartpot.api.actuators.model.entity.Actuator;
import smartpot.com.api.Exception.ApiResponse;

import java.util.List;

public interface ServiceActuator {
    List<Actuator> getAllActuators();

    Actuator getActuatorById(String id);

    List<Actuator> getActuatorsByCrop(String crop);

    Actuator createActuator(ActuatorDTO actuator) throws Exception;

    Actuator updateActuator(Actuator existingActuator , ActuatorDTO actuator);

    ResponseEntity<ApiResponse> deleteActuatorById(Actuator actuator);

    //ResponseEntity<ApiResponse> deleteActuators(List<String> ids);
}
