package app.smartpot.api.Crops.Mapper;

import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import app.smartpot.api.Crops.Model.DTO.CropDTO;
import app.smartpot.api.Crops.Model.Entity.Crop;

@Mapper(componentModel = "spring")
public interface MCrop {
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
