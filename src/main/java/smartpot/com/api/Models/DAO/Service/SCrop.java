package smartpot.com.api.Models.DAO.Service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import smartpot.com.api.Models.DAO.Repository.RCrop;
import smartpot.com.api.Models.Entity.Crop;
import smartpot.com.api.Models.Entity.User;

import java.util.List;
import java.util.Optional;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Service
/**
 * Servicio que gestiona las operaciones relacionadas con los cultivos.
 * Proporciona métodos para crear, leer, actualizar y eliminar cultivos,
 * así como búsquedas específicas por diferentes criterios.
 */
public class SCrop {

    @Autowired
    private RCrop repositoryCrop;

    /**
     * Busca un cultivo por su identificador único.
     *
     * @param id Identificador ObjectId del cultivo
     * @return Optional que contiene el cultivo si existe, vacío si no se encuentra
     */
    public Optional<Crop> getCropById(ObjectId id) {
        return repositoryCrop.findById(id);
    }
    /**
     * Obtiene todos los cultivos almacenados en el sistema.
     *
     * @return Lista de todos los cultivos existentes
     */
    public List<Crop> getCrops() {
        return repositoryCrop.findAll();
    }

    /**
     * Busca todos los cultivos asociados a un usuario específico.
     *
     * @param user Usuario propietario de los cultivos
     * @return Lista de cultivos pertenecientes al usuario
     */
    public List<Crop> getCropsByUser(User user) {
        return repositoryCrop.findByUser(user);
    }

    /**
     * Busca cultivos por su tipo .
     *
     * @param type Tipo del cultivo
     * @return Lista de cultivos que coinciden con el tipo especificado
     */
    public List<Crop> getCropsByType(String type) {
        return repositoryCrop.findByType(type);
    }

    /**
     * Cuenta el número total de cultivos que tiene un usuario.
     *
     * @param user Usuario del que se quieren contar los cultivos
     * @return Número total de cultivos del usuario
     */
    public long countCropsByUser(User user) {return repositoryCrop.countByUser(user);}

    /**
     * Busca cultivos por su estado actual.
     *
     * @param status Estado del cultivo a buscar
     * @return Lista de cultivos que se encuentran en el estado especificado
     */
    public List<Crop> getCropsByStatus(String status) {
        return repositoryCrop.findByStatus(status);
    }

    /**
     * Crea  un cultivo en el sistema.
     *
     * @param newCrop Cultivo a crear
     * @return Cultivo guardado
     */
    public Crop createCrop(Crop newCrop) {
        return repositoryCrop.save(newCrop);
    }

    /**
     * Actualiza la información de un Crop existente.
     *
     * @param id El identificador del Crop a actualizar.
     * @param updatedCrop Un objeto Crop que contiene los nuevos datos del Cultivo.
     * @return El Crop actualizado después de guardarlo en el servicio.
     * @throws ResponseStatusException Si no se encuentra un Crop con el ID proporcionado.
     */
    public Crop updatedCrop(ObjectId id, Crop updatedCrop) {
        Optional<Crop> crops = getCropById(id);
        Crop existingCrop = crops.stream().findFirst().orElse(null);
        if (existingCrop != null) {
            existingCrop.setStatus(updatedCrop.getStatus());
            existingCrop.setType(updatedCrop.getType());
            existingCrop.setUser(updatedCrop.getUser());
            return repositoryCrop.save(existingCrop);
        }
        return null;
    }


    /**
     * Elimina un cultivo existente por su identificador.
     *
     * @param id Es el  identificador del cultivo que se desea eliminar.
     * @throws ResponseStatusException Si no se encuentra el cultivo con el ID proporcionado.
     */
    public void deleteCrop(ObjectId id) {
        Optional<Crop> crops = getCropById(id);
        Crop existingCrop = crops.stream().findFirst().orElse(null);
        if (existingCrop != null) {
            repositoryCrop.delete(existingCrop);
        }

    }

}
