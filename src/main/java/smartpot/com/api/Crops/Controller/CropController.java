package smartpot.com.api.Crops.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import smartpot.com.api.Crops.Model.DAO.Service.SCropI;
import smartpot.com.api.Exception.ApiResponse;
import smartpot.com.api.Crops.Model.DTO.CropDTO;
import smartpot.com.api.Crops.Model.Entity.Crop;
import smartpot.com.api.Exception.ErrorResponse;

import java.util.List;

@RestController
@RequestMapping("/Cultivos")
public class CropController {

    private final SCropI serviceCrop;

    @Autowired
    public CropController(SCropI serviceCrop) {
        this.serviceCrop = serviceCrop;
    }

    /**
     * Obtiene todos los cultivos almacenados en el sistema.
     *
     * @return Lista de todos los cultivos existentes
     */
    @GetMapping
    public List<Crop> getAllCrops() {
        return serviceCrop.getCrops();
    }

    /**
     * Busca un cultivo por su identificador único.
     *
     * @param id Identificador ObjectId del cultivo
     * @return El cultivo encontrado
     */
    @GetMapping("/{id}")
    public Crop getCrop(@PathVariable String id) {
        return serviceCrop.getCropById(id);
    }

    /**
     * Busca cultivos por su estado actual.
     *
     * @param status Estado del cultivo a buscar
     * @return Lista de cultivos que se encuentran en el estado especificado
     */
    @GetMapping("/status/{status}")
    public List<Crop> getCropByStatus(@PathVariable String status) {
        return serviceCrop.getCropsByStatus(status);
    }

    /**
     * Busca cultivos por su tipo.
     *
     * @param type Tipo del cultivo
     * @return Lista de cultivos que coinciden con el tipo especificado
     */
    @GetMapping("/type/{type}")
    public List<Crop> getCropByType(@PathVariable String type) {
        return serviceCrop.getCropsByType(type);
    }

    /**
     * Busca todos los cultivos asociados a un usuario específico.
     *
     * @param id del Usuario propietario de los cultivos
     * @return Lista de cultivos pertenecientes al usuario
     */
    @GetMapping("/User/{id}")
    public ResponseEntity<?> getCropByUser(@PathVariable String id) {
        try {
            return new ResponseEntity<>(serviceCrop.getCropsByUser(id), HttpStatus.OK);
        } catch (Exception e)            {
            return new ResponseEntity<>(new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Cuenta el número total de cultivos que tiene un usuario.
     *
     * @param id Usuario del que se quieren contar los cultivos
     * @return Número total de cultivos del usuario
     */
    @GetMapping("/count/{id}")
    public ResponseEntity<?> countCropsByUser(@PathVariable String id) {
        try {
            return new ResponseEntity<>(serviceCrop.countCropsByUser(id), HttpStatus.OK);
        } catch (Exception e)            {
            return new ResponseEntity<>(new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Crea un nuevo cultivo.
     *
     * @param newCropDto Datos del nuevo cultivo a crear
     * @return El cultivo creado
     */
    @PostMapping("/Create")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<?> createCrop(@RequestBody CropDTO newCropDto) {
        try {
            return new ResponseEntity<>(serviceCrop.createCrop(newCropDto), HttpStatus.OK);
        } catch (Exception e)            {
            return new ResponseEntity<>(new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Actualiza un cultivo existente.
     *
     * @param id          El ID del cultivo a actualizar.
     * @param cropDetails Datos actualizados del cultivo.
     * @return El cultivo actualizado.
     */
    @PutMapping("/Update/{id}")
    public ResponseEntity<?> updateCrop(@PathVariable String id, @RequestBody CropDTO cropDetails) {
        try {
            return new ResponseEntity<>(serviceCrop.updatedCrop(id, cropDetails), HttpStatus.OK);
        } catch (Exception e)            {
            return new ResponseEntity<>(new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Elimina un cultivo existente por su ID.
     *
     * @param id El ID del cultivo a eliminar.
     */
    @DeleteMapping("/Delete/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<ApiResponse> deleteCrop(@PathVariable String id) {
        return serviceCrop.deleteCrop(serviceCrop.getCropById(id));
    }
}