package app.smartpot.api.actuators.repository;

import app.smartpot.api.actuators.model.entity.Actuator;
import app.smartpot.api.actuators.model.entity.ActuatorType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActuatorRepository extends MongoRepository<Actuator, String> {

    List<Actuator> findByCropIdOrderByCreatedAtAsc(String cropId);

    Optional<Actuator> findByIdAndCropId(String id, String cropId);

    Optional<Actuator> findByCropIdAndType(String cropId, ActuatorType type);

    boolean existsByCropIdAndType(String cropId, ActuatorType type);

    void deleteByCropId(String cropId);
}
