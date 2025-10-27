package smartpot.com.api.Actuators.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smartpot.com.api.Actuators.Service.SActuator;

@RestController
@RequestMapping("Actuators")
public class ActuatorController {

    private final SActuator serviceActuator;

    @Autowired
    public ActuatorController(SActuator serviceActuator) {
        this.serviceActuator = serviceActuator;
    }

}
