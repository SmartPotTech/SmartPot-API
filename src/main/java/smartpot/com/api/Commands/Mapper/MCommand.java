package smartpot.com.api.Commands.Mapper;

import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import smartpot.com.api.Commands.Model.DTO.CommandDTO;
import smartpot.com.api.Commands.Model.Entity.Command;

@Mapper(componentModel = "spring")
public interface MCommand {
    MCommand INSTANCE = Mappers.getMapper(MCommand.class);

    @Mapping(source = "id", target = "id", qualifiedByName = "stringToObjectId")
    @Mapping(source = "crop", target = "crop", qualifiedByName = "stringToObjectId")
    @Mapping(source = "actuator", target = "actuator", qualifiedByName = "stringToObjectId")
    Command toEntity(CommandDTO commandDTO);

    @Mapping(source = "id", target = "id", qualifiedByName = "objectIdToString")
    @Mapping(source = "crop", target = "crop", qualifiedByName = "objectIdToString")
    @Mapping(source = "actuator", target = "actuator", qualifiedByName = "objectIdToString")
    CommandDTO toDTO(Command command);

    @org.mapstruct.Named("objectIdToString")
    default String objectIdToString(ObjectId objectId) {
        return objectId != null ? objectId.toHexString() : null;
    }

    @org.mapstruct.Named("stringToObjectId")
    default ObjectId stringToObjectId(String id) {
        return id != null ? new ObjectId(id) : null;
    }
}
