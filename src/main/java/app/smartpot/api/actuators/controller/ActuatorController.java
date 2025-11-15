package app.smartpot.api.actuators.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import app.smartpot.api.actuators.model.dto.ActuatorDTO;
import app.smartpot.api.actuators.model.entity.Actuator;
import app.smartpot.api.actuators.service.ServiceActuator;
import app.smartpot.api.exception.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/Actuators")
public class ActuatorController {

    private final ServiceActuator serviceActuator;

    @Autowired
    public ActuatorController(ServiceActuator serviceActuator) {
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

    @PostMapping("/Create")
    public Actuator createActuator(@RequestBody ActuatorDTO actuator) throws Exception {
        return serviceActuator.createActuator(actuator);
    }

    @PutMapping("/Update/{id}")
    public Actuator updateActuator(@PathVariable String id, @RequestBody ActuatorDTO actuator) throws Exception {
        return serviceActuator.updateActuator(serviceActuator.getActuatorById(id), actuator);
    }

    @DeleteMapping("/Delete/{id}")
    public ResponseEntity<ApiResponse> deleteActuator(@PathVariable String id) throws Exception {
        return serviceActuator.deleteActuatorById(serviceActuator.getActuatorById(id));
    }

}
