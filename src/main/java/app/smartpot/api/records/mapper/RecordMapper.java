package app.smartpot.api.records.mapper;

import app.smartpot.api.records.model.dto.RecordDTO;
import app.smartpot.api.records.model.entity.History;
import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Mapper(componentModel = "spring")
public interface RecordMapper {
    RecordMapper INSTANCE = Mappers.getMapper(RecordMapper.class);

    @Mapping(source = "id", target = "id", qualifiedByName = "stringToObjectId")
    @Mapping(source = "crop", target = "crop", qualifiedByName = "stringToObjectId")
    @Mapping(source = "date", target = "date", qualifiedByName = "stringToDate")
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

    @org.mapstruct.Named("stringToDate")
    default Date stringToDate(String date) {
        if (date == null) return null;
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(date);
        } catch (ParseException e) {
            throw new RuntimeException("Invalid date format: " + date);
        }
    }
}
