package app.smartpot.api.channels.repository;

import app.smartpot.api.channels.model.entity.ChannelLink;
import app.smartpot.api.channels.model.entity.ChannelType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelLinkRepository extends MongoRepository<ChannelLink, String> {

    List<ChannelLink> findByUserId(String userId);

    List<ChannelLink> findByUserIdAndEnabledTrue(String userId);

    Optional<ChannelLink> findByIdAndUserId(String id, String userId);

    Optional<ChannelLink> findByUserIdAndType(String userId, ChannelType type);

    Optional<ChannelLink> findByTypeAndAddress(ChannelType type, String address);

    void deleteByUserId(String userId);
}
