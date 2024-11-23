package smartpot.com.api.Controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import smartpot.com.api.Exception.ApiResponse;
import smartpot.com.api.Models.DAO.Service.SCrop;
import smartpot.com.api.Models.DTO.CropDTO;
import smartpot.com.api.Models.Entity.Crop;

import java.util.List;

@RestController
@RequestMapping("/Cultivos")
public class CropController {

    private final SCrop serviceCrop;

    @Autowired
    public CropController(SCrop serviceCrop) {
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
    public List<Crop> getCropByUser(@PathVariable String id) {
        return serviceCrop.getCropsByUser(id);
    }

    /**
     * Cuenta el número total de cultivos que tiene un usuario.
     *
     * @param id Usuario del que se quieren contar los cultivos
     * @return Número total de cultivos del usuario
     */
    @GetMapping("/count")
    public long countCropsByUser(@RequestParam String id) {
        return serviceCrop.countCropsByUser(id);
    }

    /**
     * Crea un nuevo cultivo.
     *
     * @param newCropDto Datos del nuevo cultivo a crear
     * @return El cultivo creado
     */
    @PostMapping("/Create")
    @ResponseStatus(HttpStatus.CREATED)
    public Crop createCrop(@RequestBody CropDTO newCropDto) {
        return serviceCrop.createCrop(newCropDto);
    }

    /**
     * Actualiza un cultivo existente.
     *
     * @param id          El ID del cultivo a actualizar.
     * @param cropDetails Datos actualizados del cultivo.
     * @return El cultivo actualizado.
     */
    @PutMapping("/Update/{id}")
    public Crop updateCrop(@PathVariable String id, @RequestBody CropDTO cropDetails) {
        return serviceCrop.updatedCrop(id, cropDetails);
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