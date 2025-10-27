package smartpot.com.api.Actuators.Service;

import org.bson.types.ObjectId;
import smartpot.com.api.Actuators.Model.Entity.Actuator;

import java.util.List;

public class SActuator implements SActuatorI {
    @Override
    public List<Actuator> getAllActuators() {
        return List.of();
    }

    @Override
    public Actuator getActuatorById(ObjectId id) {
        return null;
    }

    @Override
    public List<Actuator> getActuatorsByCrop(ObjectId crop) {
        return List.of();
    }
}
