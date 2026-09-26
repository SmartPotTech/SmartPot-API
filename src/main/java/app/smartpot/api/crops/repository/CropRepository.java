package app.smartpot.api.crops.repository;

import app.smartpot.api.crops.model.entity.Crop;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CropRepository extends MongoRepository<Crop, String> {

    List<Crop> findByOwnerIdOrderByCreatedAtAsc(String ownerId);

    Optional<Crop> findByIdAndOwnerId(String id, String ownerId);

    long countByOwnerId(String ownerId);

    List<Crop> findByDeviceKeyCiphertextNotNull();
}
