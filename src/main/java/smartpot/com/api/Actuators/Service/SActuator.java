package smartpot.com.api.Actuators.Service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import smartpot.com.api.Actuators.Model.Entity.Actuator;
import smartpot.com.api.Exception.ApiResponse;

import java.util.List;

@Slf4j
@Data
@Builder
@Service
public class SActuator implements SActuatorI {

    @Override
    public List<Actuator> getAllActuators() {
        return List.of();
    }

    @Override
    public Actuator getActuatorById(String id) {
        return null;
    }

    @Override
    public List<Actuator> getActuatorsByCrop(String crop) {
        return List.of();
    }

    @Override
    public Actuator CreateActuator(Actuator actuator) {
        return null;
    }

    @Override
    public Actuator UpdateActuator(Actuator actuator) {
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
