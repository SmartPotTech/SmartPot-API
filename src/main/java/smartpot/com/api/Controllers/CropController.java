package smartpot.com.api.Controllers;

import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import smartpot.com.api.Models.DAO.Service.SCrop;
import smartpot.com.api.Models.Entity.Crop;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import smartpot.com.api.Models.Entity.User;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/Cultivos")
public class CropController {

    @Autowired
    private SCrop serviceCrop;

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
     * @return Optional que contiene el cultivo si existe, vacío si no se encuentra
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
     * Busca todos los cultivos asociados a un usuario específico.
     *
     * @param id del Usuario propietario de los cultivos
     * @return Lista de cultivos pertenecientes al usuario
     */
    @GetMapping("/User/{user}")
    public List<Crop> getCropByUser(@PathVariable String id) {
        return serviceCrop.getCropsByUser(id);
    }

    /**
     * Busca cultivos por su tipo .
     *
     * @param type Tipo del cultivo
     * @return Lista de cultivos que coinciden con el tipo especificado
     */
    @GetMapping("/type/{type}")
    public List<Crop> getCropByType(@PathVariable String type) {
        return serviceCrop.getCropsByType(type);
    }

    /**
     * Cuenta el número total de cultivos que tiene un usuario.
     *
     * @param id Usuario del que se quieren contar los cultivos
     * @return Número total de cultivos del usuario
     */
    @GetMapping("/count")
    public long countCropsByUser(String id) {
        return serviceCrop.countCropsByUser(id);
    }

    /**
     * Crea un nuevo cultivo
     *
     * @param newCrop El objeto cultivo que contiene los datos del cultivo que se debe guardar
     * @return El objeto cultivo creado.
     */
    @PostMapping("/Create")
    @ResponseStatus(HttpStatus.CREATED)
    public Crop createCrop(@RequestBody Crop newCrop) {
        return serviceCrop.createCrop(newCrop);
    }

    /**
     * Actualiza un cultivo existente.
     *
     * @param id El ID del cultivo a actualizar.
     * @param cropDetails El objeto Crop que contiene los nuevos datos.
     * @return El objeto Crop actualizado.
     */
    @PutMapping("/Update/{id}")
    public Crop updateCrop(@PathVariable ObjectId id, @RequestBody Crop cropDetails) {
        return serviceCrop.updatedCrop(id,cropDetails);
    }

    /**
     * Elimina un cultivo existente por su ID.
     *
     * @param id El ID del cultivo a eliminar.
     */
    @DeleteMapping("/Delete/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable ObjectId id) {
        serviceCrop.deleteCrop(id);
    }
}