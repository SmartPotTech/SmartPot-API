package smartpot.com.api.Actuators.Repository;

import jakarta.validation.constraints.NotNull;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import smartpot.com.api.Actuators.Model.Entity.Actuator;

import java.util.List;
import java.util.Optional;

@Repository
public interface RActuator extends MongoRepository<Actuator, ObjectId> {

    @Query("{'_id': ?0}")
    Optional<Actuator> findById(
            @NotNull(message = "Se necesita el id del actuador")
            ObjectId id);

    List<Actuator> findByCrop(
            @NotNull(message = "El registro debe estar asociado a un cultivo")
            ObjectId crop);
}
