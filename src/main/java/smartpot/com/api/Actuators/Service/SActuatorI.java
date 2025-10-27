package smartpot.com.api.Actuators.Service;

import org.bson.types.ObjectId;
import smartpot.com.api.Actuators.Model.Entity.Actuator;

import java.util.List;

public interface SActuatorI {
    List<Actuator> getAllActuators();

    Actuator getActuatorById(ObjectId id);

    List<Actuator> getActuatorsByCrop(ObjectId crop);
}
