package app.smartpot.api.channels.repository;

import app.smartpot.api.channels.model.entity.ChannelType;
import app.smartpot.api.channels.model.entity.CropChannel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CropChannelRepository extends MongoRepository<CropChannel, String> {

    Optional<CropChannel> findByCropIdAndType(String cropId, ChannelType type);

    List<CropChannel> findByCropId(String cropId);

    List<CropChannel> findByEnabledTrue();

    List<CropChannel> findByTypeAndRecipientsAddress(ChannelType type, String address);

    void deleteByCropId(String cropId);

    void deleteByOwnerId(String ownerId);
}
