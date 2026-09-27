package app.smartpot.api.virtualdevices.repository;

import app.smartpot.api.virtualdevices.model.entity.VirtualDevice;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface VirtualDeviceRepository extends MongoRepository<VirtualDevice, String> {

    Optional<VirtualDevice> findByCropId(String cropId);

    long countByOwnerId(String ownerId);

    void deleteByCropId(String cropId);
}
