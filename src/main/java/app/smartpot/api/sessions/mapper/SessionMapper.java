package app.smartpot.api.sessions.mapper;

import app.smartpot.api.sessions.model.dto.SessionDTO;
import app.smartpot.api.sessions.model.entity.Session;
import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface SessionMapper {
    SessionMapper INSTANCE = Mappers.getMapper(SessionMapper.class);

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
