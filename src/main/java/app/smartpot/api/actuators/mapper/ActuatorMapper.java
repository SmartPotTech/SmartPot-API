package app.smartpot.api.actuators.mapper;

import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import app.smartpot.api.actuators.model.dto.ActuatorDTO;
import app.smartpot.api.actuators.model.entity.Actuator;

@Mapper(componentModel = "spring")
public interface ActuatorMapper {
    ActuatorMapper INSTANCE = Mappers.getMapper(ActuatorMapper.class);

    @Mapping(source = "id", target = "id", qualifiedByName = "stringToObjectId")
    @Mapping(source = "crop", target = "crop", qualifiedByName = "stringToObjectId")
    Actuator toEntity(ActuatorDTO recordDTO);

    @Mapping(source = "id", target = "id", qualifiedByName = "objectIdToString")
    @Mapping(source = "crop", target = "crop", qualifiedByName = "objectIdToString")
    ActuatorDTO toDTO(Actuator history);

    @org.mapstruct.Named("objectIdToString")
    default String objectIdToString(ObjectId objectId) {
        return objectId != null ? objectId.toHexString() : null;
    }

    @org.mapstruct.Named("stringToObjectId")
    default ObjectId stringToObjectId(String id) {
        return id != null ? new ObjectId(id) : null;
    }
}
