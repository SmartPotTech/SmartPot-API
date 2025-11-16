package app.smartpot.api.actuators.service;

import org.springframework.http.ResponseEntity;
import app.smartpot.api.actuators.model.dto.ActuatorDTO;
import app.smartpot.api.exception.ApiResponse;

import java.util.List;

public interface ServiceActuator {
    List<ActuatorDTO> getAllActuators() throws Exception;

    ActuatorDTO getActuatorById(String id) throws Exception;

    List<ActuatorDTO> getActuatorsByCrop(String crop) throws Exception;

    ActuatorDTO createActuator(ActuatorDTO actuator) throws Exception;

    ActuatorDTO updateActuator(String id , ActuatorDTO actuator) throws Exception;

    String deleteActuatorById(String id) throws Exception;

    //ResponseEntity<ApiResponse> deleteActuators(List<String> ids);
}
