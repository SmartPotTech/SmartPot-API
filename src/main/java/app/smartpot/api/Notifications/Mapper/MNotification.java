package app.smartpot.api.Notifications.Mapper;

import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import app.smartpot.api.Notifications.Model.DTO.NotificationDTO;
import app.smartpot.api.Notifications.Model.Entity.Notification;

@Mapper(componentModel = "spring")
public interface MNotification {
    MNotification INSTANCE = Mappers.getMapper(MNotification.class);

    @Mapping(source = "id", target = "id", qualifiedByName = "stringToObjectId")
    @Mapping(source = "user", target = "user", qualifiedByName = "stringToObjectId")
    Notification toEntity(NotificationDTO notificationDTO);

    @Mapping(source = "id", target = "id", qualifiedByName = "objectIdToString")
    @Mapping(source = "user", target = "user", qualifiedByName = "objectIdToString")
    NotificationDTO toDTO(Notification notification);

    @org.mapstruct.Named("objectIdToString")
    default String objectIdToString(ObjectId objectId) {
        return objectId != null ? objectId.toHexString() : null;
    }

    @org.mapstruct.Named("stringToObjectId")
    default ObjectId stringToObjectId(String id) {
        return id != null ? new ObjectId(id) : null;
    }
}
