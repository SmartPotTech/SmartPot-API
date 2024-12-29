package smartpot.com.api.Crops.Model.DAO.Repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import smartpot.com.api.Crops.Model.Entity.Crop;

import java.util.List;

/**
 * Repositorio para la gestión de la entidad {@link Crop} en la base de datos MongoDB.
 * <p>
 * Esta interfaz extiende {@link MongoRepository}, lo que proporciona métodos CRUD básicos
 * para interactuar con la base de datos MongoDB de manera sencilla.
 * Además, incluye consultas personalizadas para buscar cultivos por diferentes atributos
 * como tipo, estado y usuario.
 * </p>
 * <p>
 * Las consultas personalizadas están anotadas con {@link Query}, que utilizan la sintaxis de MongoDB
 * para realizar búsquedas más específicas en los campos de la colección de cultivos.
 * </p>
 *
 * @see MongoRepository
 * @see Crop
 */
@Repository
public interface RCrop extends MongoRepository<Crop, ObjectId> {
    /**
     * Busca una lista de cultivos cuyo tipo coincida exactamente con el tipo proporcionado.
     *
     * <p>Esta consulta busca cultivos cuyo campo 'type' coincida exactamente con el valor del parámetro
     * proporcionado, lo que permite filtrar los cultivos por su tipo.</p>
     *
     * @param type El tipo de cultivo a buscar en el campo 'type'.
     * @return Una lista de cultivos cuyo tipo coincida exactamente.
     * @see Query
     */
    @Query("{ 'type' : ?0 }")
    List<Crop> findByType(String type);

    /**
     * Busca una lista de cultivos cuyo estado coincida exactamente con el estado proporcionado.
     *
     * <p>Esta consulta busca cultivos cuyo campo 'status' coincida exactamente con el valor del parámetro
     * proporcionado, lo que permite filtrar los cultivos por su estado (por ejemplo, 'activo', 'inactivo', etc.).</p>
     *
     * @param status El estado del cultivo a buscar en el campo 'status'.
     * @return Una lista de cultivos cuyo estado coincida exactamente.
     * @see Query
     */
    @Query("{ 'status' : ?0 }")
    List<Crop> findByStatus(String status);

    /**
     * Busca una lista de cultivos cuyo campo 'user' coincida con el ID de usuario proporcionado.
     *
     * <p>Esta consulta busca cultivos cuyo campo 'user' coincida con el ID del usuario proporcionado.
     * Permite filtrar los cultivos asociados a un usuario específico.</p>
     *
     * @param id El ID del usuario (en formato {@link ObjectId}) cuyo campo 'user' se desea buscar.
     * @return Una lista de cultivos asociados al usuario con el ID proporcionado.
     * @see Query
     * @see ObjectId
     */
    @Query("{ 'user': ?0}")
    List<Crop> findByUser(ObjectId id);
}
