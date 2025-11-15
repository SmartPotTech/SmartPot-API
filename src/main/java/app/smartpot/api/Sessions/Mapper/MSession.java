package app.smartpot.api.Sessions.Mapper;

import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import app.smartpot.api.Sessions.Model.DTO.SessionDTO;
import app.smartpot.api.Sessions.Model.Entity.Session;

@Mapper(componentModel = "spring")
public interface MSession {
    MSession INSTANCE = Mappers.getMapper(MSession.class);

    @Mapping(source = "id", target = "id", qualifiedByName = "stringToObjectId")
    Session toEntity(SessionDTO sessionDTO);

    @Mapping(source = "id", target = "id", qualifiedByName = "objectIdToString")
    SessionDTO toDTO(Session session);

    @org.mapstruct.Named("objectIdToString")
    default String objectIdToString(ObjectId objectId) {
        return objectId != null ? objectId.toHexString() : null;
    }

    @org.mapstruct.Named("stringToObjectId")
    default ObjectId stringToObjectId(String id) {
        return id != null ? new ObjectId(id) : null;
    }
}
