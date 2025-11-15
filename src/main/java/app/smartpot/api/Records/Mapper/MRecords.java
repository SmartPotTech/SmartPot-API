package app.smartpot.api.Records.Mapper;

import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import app.smartpot.api.Records.Model.DTO.RecordDTO;
import app.smartpot.api.Records.Model.Entity.History;

@Mapper(componentModel = "spring")
public interface MRecords {
    MRecords INSTANCE = Mappers.getMapper(MRecords.class);

    @Mapping(source = "id", target = "id", qualifiedByName = "stringToObjectId")
    @Mapping(source = "crop", target = "crop", qualifiedByName = "stringToObjectId")
    History toEntity(RecordDTO recordDTO);

    @Mapping(source = "id", target = "id", qualifiedByName = "objectIdToString")
    @Mapping(source = "crop", target = "crop", qualifiedByName = "objectIdToString")
    RecordDTO toDTO(History history);

    @org.mapstruct.Named("objectIdToString")
    default String objectIdToString(ObjectId objectId) {
        return objectId != null ? objectId.toHexString() : null;
    }

    @org.mapstruct.Named("stringToObjectId")
    default ObjectId stringToObjectId(String id) {
        return id != null ? new ObjectId(id) : null;
    }
}
