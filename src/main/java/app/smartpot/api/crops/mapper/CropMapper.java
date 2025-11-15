package app.smartpot.api.crops.mapper;

import app.smartpot.api.crops.model.dto.CropDTO;
import app.smartpot.api.crops.model.entity.Crop;
import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CropMapper {
    @Mapping(source = "id", target = "id", qualifiedByName = "stringToObjectId")
    @Mapping(source = "user", target = "user", qualifiedByName = "stringToObjectId")
    Crop toEntity(CropDTO cropDTO);

    @Mapping(source = "id", target = "id", qualifiedByName = "objectIdToString")
    @Mapping(source = "user", target = "user", qualifiedByName = "objectIdToString")
    CropDTO toDTO(Crop crop);

    @org.mapstruct.Named("objectIdToString")
    default String objectIdToString(ObjectId objectId) {
        return objectId != null ? objectId.toHexString() : null;
    }

    @org.mapstruct.Named("stringToObjectId")
    default ObjectId stringToObjectId(String id) {
        return id != null ? new ObjectId(id) : null;
    }
}
