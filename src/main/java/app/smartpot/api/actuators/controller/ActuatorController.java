package app.smartpot.api.actuators.controller;

import app.smartpot.api.responses.ErrorResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import app.smartpot.api.actuators.model.dto.ActuatorDTO;
import app.smartpot.api.actuators.service.ActuatorService;

@RestController
@RequestMapping("/Actuators")
public class ActuatorController {

    // TODO: Documentar mejor, por ejemplo como en UserController

    private final ActuatorService serviceActuator;

    @Autowired
    public ActuatorController(ActuatorService serviceActuator) {
        this.serviceActuator = serviceActuator;
    }


    /**
     * Obtiene todos los actuadores almacenados en el sistema.
     *
     * @return Lista de todos los actuadores existentes
     */
    @GetMapping("/All")
    public ResponseEntity<?> getAllActuators() {
        try {
            return new ResponseEntity<>(serviceActuator.getAllActuators(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al buscar los actuadores [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Obtiene el actuador con el identificador proporcionado.
     *
     * @param id Es el identificador del actuador
     * @return El actuador que tenga la id suministrada
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getActuatorById(@PathVariable String id) {
        try {
            return new ResponseEntity<>(serviceActuator.getActuatorById(id), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al buscar los actuadores [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Obtiene todos los actuadores almacenados en el sistema para un cultivo especificado.
     *
     * @param id Es el identificador del cultivo
     * @return Lista de todos los actuadores existentes
     */
    @GetMapping("/crop/{id}")
    public ResponseEntity<?> getActuatorByCrop(@PathVariable String id) {
        try {
            return new ResponseEntity<>(serviceActuator.getActuatorsByCrop(id), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al buscar el actuadores [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }

    }

    @PostMapping("/Create")
    public ResponseEntity<?> createActuator(@RequestBody ActuatorDTO actuator) {
        try {
            return new ResponseEntity<>(serviceActuator.createActuator(actuator), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al crear el actuador [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    @PutMapping("/Update/{id}")
    public ResponseEntity<?> updateActuator(@PathVariable String id, @RequestBody ActuatorDTO actuator) {
        try {
            return new ResponseEntity<>(serviceActuator.updateActuator(serviceActuator.getActuatorById(id).getId(), actuator), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al actualizar el actuador [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/Delete/{id}")
    public ResponseEntity<?> deleteActuator(@PathVariable String id) {
        try {
            return new ResponseEntity<>(serviceActuator.deleteActuatorById(serviceActuator.getActuatorById(id).getId()), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al eliminar el actuador [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

}
