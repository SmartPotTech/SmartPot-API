package app.smartpot.api.actuators.repository;

import jakarta.validation.constraints.NotNull;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import app.smartpot.api.actuators.model.entity.Actuator;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActuatorRepository extends MongoRepository<Actuator, ObjectId> {

    /**
     * Busca el registro de actuador con la id espesificada
     *
     * @param id El identificado del registro del actuador.
     * @return Devuelve el actuador, en caso de ser encontrado.
     */
    @Query("{'_id': ?0}")
    Optional<Actuator> findById(
            @NotNull(message = "Se necesita el id del actuador")
            ObjectId id);

    /**
     * Busca los registros de actuadorres con el identificador del cultivo espesificado
     *
     * @param crop El identificado del cultivo relacionado a registros del actuadores.
     * @return Devuelve una lista de actuadores.
     */
    List<Actuator> findByCrop(
            @NotNull(message = "El registro debe estar asociado a un cultivo")
            ObjectId crop);
}
