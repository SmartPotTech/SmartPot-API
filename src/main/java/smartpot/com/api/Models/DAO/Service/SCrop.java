package smartpot.com.api.Models.DAO.Service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import smartpot.com.api.Models.DAO.Repository.RCrop;
import smartpot.com.api.Models.Entity.*;
import smartpot.com.api.Validation.ErrorResponse;
import smartpot.com.api.Validation.Exception;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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
    @Autowired
    private SUser serviceUser;


    /**
     * Este metodo maneja el ID como un ObjectId de MongoDB. Si el ID es válido como ObjectId,
     * se utiliza en la búsqueda como tal.
     *
     * @param id El identificador del cultivo a buscar. Se recibe como String para evitar errores de conversion.
     * @return El cultivo correspondiente al id proporcionado.
     * @throws ResponseStatusException Si el id proporcionado no es válido o no se encuentra el cultivo.
     * @throws Exception Si no se encuentra el cultivo con el id proporcionado.
     */
    public Crop getCropById(String id) {
        if(!ObjectId.isValid(id)) {
            throw new Exception(new ErrorResponse(
                    "El cultivo con id '"+ id +"' no es válido. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
        return repositoryCrop.findById(new ObjectId(id))
                .orElseThrow(() -> new Exception(
                        new ErrorResponse("El cultivo con id '"+ id +"' no fue encontrado.",
                                HttpStatus.NOT_FOUND.value())
                ));
    }
    /**
     * Obtiene todos los cultivos almacenados en el sistema.
     *
     * @return Lista de todos los cultivos existentes
     */
    public List<Crop> getCrops() {
        List<Crop> crops = repositoryCrop.findAll();
        if (crops == null || crops.isEmpty()) {
            throw new Exception(new ErrorResponse(
                    "No se encontro ningun cultivo en la db",
                    HttpStatus.NOT_FOUND.value()
            ));
        }
        return crops;
    }

    /**
     * Busca todos los cultivos asociados a un usuario específico.
     *
     * @param id del  Usuario propietario de los cultivos
     * @return Lista de cultivos pertenecientes al usuario
     */
    public List<Crop> getCropsByUser(String id) {
        User user = serviceUser.getUserById(id);
            List<Crop> crops = repositoryCrop.findAll();
            List<Crop> cropsUser = new ArrayList<>();
            for(Crop crop : crops){
                if(crop.getUser().equals(user.getId())){
                    cropsUser.add(crop);
                }
            }
            if (cropsUser.isEmpty()) {
                throw new Exception(new ErrorResponse(
                        "No se encuentra ningun Cultivo perteneciente al usuario con el id: '" + id + "'.",
                        HttpStatus.NOT_FOUND.value()
                ));
            }
            return cropsUser;
    }

    /**
     * Busca cultivos por su tipo .
     *
     * @param type Tipo del cultivo
     * @return Lista de cultivos que coinciden con el tipo especificado
     */
    public List<Crop> getCropsByType(String type) {
        List<Crop> cropsByType = repositoryCrop.findByType(type);
        if(cropsByType.isEmpty()){
            throw new Exception(new ErrorResponse(
                    "No se encuentra ningun Cultivo perteneciente al type: '" + type + "'.",
                    HttpStatus.NOT_FOUND.value()
            ));
        }else
    return repositoryCrop.findByType(type);
    }

    /**
     * Cuenta el número total de cultivos que tiene un usuario.
     *
     * @param id del Usuario del que se quieren contar los cultivos
     * @return Número total de cultivos del usuario
     */
    public long countCropsByUser(String id) { return getCropsByUser(id).size();

    }

    /**
     * Busca cultivos por su estado actual.
     *
     * @param status Estado del cultivo a buscar
     * @return Lista de cultivos que se encuentran en el estado especificado
     */
    public List<Crop> getCropsByStatus(String status) {
        List<Crop> cropsByStatus = repositoryCrop.findByStatus(status);
        if(cropsByStatus.isEmpty()){
            throw new Exception(new ErrorResponse(
                    "No se encuentra ningun Cultivo perteneciente al status: '" + status + "'.",
                    HttpStatus.NOT_FOUND.value()
            ));
        }else
        return cropsByStatus;
    }

    /**
     * Crea  un cultivo en el sistema.
     *
     * @param newCrop Cultivo a crear
     * @return Cultivo guardado
     */
    public Crop createCrop(Crop newCrop) {
        boolean isValidStatus = Stream.of(Status.values())
                .anyMatch(r -> r.name().equalsIgnoreCase(newCrop.getStatus().name()));
        if (!isValidStatus) {
            throw new Exception(new ErrorResponse(
                    "El Status '" + newCrop.getStatus().name() + "' no es válido.",
                    HttpStatus.BAD_REQUEST.value()));
        }
        boolean isValidSType = Stream.of(Type.values())
                .anyMatch(r -> r.name().equalsIgnoreCase(newCrop.getStatus().name()));
        if (!isValidSType) {
            throw new Exception(new ErrorResponse(
                    "El Type '" + newCrop.getType().name() + "' no es válido.",
                    HttpStatus.BAD_REQUEST.value()));
        }
        return repositoryCrop.save(newCrop);
    }

    /**
     * Actualiza la información de un Crop existente.
     *
     * @param id El identificador del Crop a actualizar.
     * @param updatedCrop Un objeto Crop que contiene los nuevos datos del Cultivo.
     * @return El Crop actualizado después de guardarlo en el servicio.
     *
     */
    public Crop updatedCrop(String id, Crop updatedCrop) {
        boolean isValidStatus = Stream.of(Status.values())
                .anyMatch(r -> r.name().equalsIgnoreCase(updatedCrop.getStatus().name()));
        if (!isValidStatus) {
            throw new Exception(new ErrorResponse(
                    "El Status '" + updatedCrop.getStatus().name() + "' no es válido.",
                    HttpStatus.BAD_REQUEST.value()));
        }
        boolean isValidSType = Stream.of(Type.values())
                .anyMatch(r -> r.name().equalsIgnoreCase(updatedCrop.getStatus().name()));
        if (!isValidSType) {
            throw new Exception(new ErrorResponse(
                    "El Type '" + updatedCrop.getType().name() + "' no es válido.",
                    HttpStatus.BAD_REQUEST.value()));
        }
                return repositoryCrop.updateUser(getCropById(id).getId(), updatedCrop);
            }




    /**
     * Elimina un cultivo existente por su identificador.
     *
     * @param id Es el  identificador del cultivo que se desea eliminar.
     */
    public void deleteCrop(String id) {
        if (!ObjectId.isValid(id)) {
            throw new Exception(new ErrorResponse(
                    "El ID '" + id + "' no es válido. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
        Crop existingCrop = repositoryCrop.findById(new ObjectId(id))
                .orElseThrow(() -> new Exception(
                        new ErrorResponse("El usuario con ID '" + id + "' no fue encontrado.",
                                HttpStatus.NOT_FOUND.value())
                ));

        repositoryCrop.deleteById(existingCrop.getId());
    }
    }


