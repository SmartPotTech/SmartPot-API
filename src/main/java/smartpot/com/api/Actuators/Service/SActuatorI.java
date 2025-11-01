package smartpot.com.api.Actuators.Service;

import smartpot.com.api.Actuators.Model.Entity.Actuator;

import java.util.List;

public interface SActuatorI {
    List<Actuator> getAllActuators();

    Actuator getActuatorById(String id);

    List<Actuator> getActuatorsByCrop(String crop);
}
