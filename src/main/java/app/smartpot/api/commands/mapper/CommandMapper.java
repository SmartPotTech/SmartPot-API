package app.smartpot.api.commands.mapper;

import app.smartpot.api.commands.model.dto.CommandDTO;
import app.smartpot.api.commands.model.entity.Command;
import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Mapper(componentModel = "spring")
public interface CommandMapper {
    CommandMapper INSTANCE = Mappers.getMapper(CommandMapper.class);

    @Mapping(source = "id", target = "id", qualifiedByName = "stringToObjectId")
    @Mapping(source = "crop", target = "crop", qualifiedByName = "stringToObjectId")
    @Mapping(source = "actuator", target = "actuator", qualifiedByName = "stringToObjectId")
    @Mapping(source = "dateCreated", target = "dateCreated", qualifiedByName = "stringToDate")
    @Mapping(source = "dateExecuted", target = "dateExecuted", qualifiedByName = "stringToDate")
    Command toEntity(CommandDTO commandDTO);

    @Mapping(source = "id", target = "id", qualifiedByName = "objectIdToString")
    @Mapping(source = "crop", target = "crop", qualifiedByName = "objectIdToString")
    @Mapping(source = "actuator", target = "actuator", qualifiedByName = "objectIdToString")
    @Mapping(source = "dateCreated", target = "dateCreated", qualifiedByName = "dateToString")
    @Mapping(source = "dateExecuted", target = "dateExecuted", qualifiedByName = "dateToString")
    CommandDTO toDTO(Command command);

    @org.mapstruct.Named("objectIdToString")
    default String objectIdToString(ObjectId objectId) {
        return objectId != null ? objectId.toHexString() : null;
    }

    @org.mapstruct.Named("stringToObjectId")
    default ObjectId stringToObjectId(String id) {
        return id != null ? new ObjectId(id) : null;
    }

    @org.mapstruct.Named("stringToDate")
    default Date stringToDate(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }
        try {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return formatter.parse(dateString);
        } catch (ParseException e) {
            throw new RuntimeException("Error al convertir la fecha: " + dateString, e);
        }
    }

    @org.mapstruct.Named("dateToString")
    default String dateToString(Date date) {
        if (date == null) {
            return null;
        }
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return formatter.format(date);
    }
}
