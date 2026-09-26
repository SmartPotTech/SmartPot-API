package app.smartpot.api.commands.repository;

import app.smartpot.api.commands.model.entity.Command;
import app.smartpot.api.commands.model.entity.CommandStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommandRepository extends MongoRepository<Command, String> {

    List<Command> findByCropIdOrderByCreatedAtDesc(String cropId, Pageable pageable);

    Optional<Command> findByIdAndCropId(String id, String cropId);

    List<Command> findByStatusAndSentAtBefore(CommandStatus status, Instant limit);

    boolean existsByActuatorIdAndStatusIn(String actuatorId, Collection<CommandStatus> statuses);

    void deleteByCropId(String cropId);
}
