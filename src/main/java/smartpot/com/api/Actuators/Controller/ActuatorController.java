package smartpot.com.api.Actuators.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smartpot.com.api.Actuators.Model.Entity.Actuator;
import smartpot.com.api.Actuators.Service.SActuatorI;

import java.util.List;

@RestController
@RequestMapping("/Actuators")
public class ActuatorController {

    private final SActuatorI serviceActuator;

    @Autowired
    public ActuatorController(SActuatorI serviceActuator) {
        this.serviceActuator = serviceActuator;
    }


    /**
     * Obtiene todos los actuadores almacenados en el sistema.
     *
     * @return Lista de todos los actuadores existentes
     */
    @GetMapping("/All")
    public List<Actuator> getAllActuators() {
        return serviceActuator.getAllActuators();
    }

    /**
     * Obtiene el actuadores con el identificador proporcionado.
     *
     * @param id Es el identificador del la instruccion del actuador
     * @return Lista de todos los actuadores existentes
     */
    @GetMapping("/{id}")
    public Actuator getActuatorById(@PathVariable String id) {
        return serviceActuator.getActuatorById(id);
    }

    /**
     * Obtiene todos los actuadores almacenados en el sistema para un cultivo espesificado.
     *
     * @param id Es el identificador del cultivo
     * @return Lista de todos los actuadores existentes
     */
    @GetMapping("/crop/{id}")
    public List<Actuator> getActuatorByCrop(@PathVariable String id) {
        return serviceActuator.getActuatorsByCrop(id);
    }

}
