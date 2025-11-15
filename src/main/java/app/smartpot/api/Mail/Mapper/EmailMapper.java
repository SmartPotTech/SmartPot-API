package app.smartpot.api.Mail.Mapper;

import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import app.smartpot.api.Mail.Model.DTO.EmailDTO;
import app.smartpot.api.Mail.Model.Entity.EmailDetails;

import java.text.SimpleDateFormat;
import java.util.Date;

@Mapper(componentModel = "spring")
public interface EmailMapper {
    @Mapping(source = "id", target = "id", qualifiedByName = "stringToObjectId")
    @Mapping(source = "sendDate", target = "sendDate", qualifiedByName = "stringToDate")
    @Mapping(source = "sent", target = "sent", qualifiedByName = "stringToBoolean")
    EmailDetails toEntity(EmailDTO emailDTO);

    @Mapping(source = "id", target = "id", qualifiedByName = "objectIdToString")
    @Mapping(source = "sendDate", target = "sendDate", qualifiedByName = "dateToString")
    @Mapping(source = "sent", target = "sent", qualifiedByName = "booleanToString")
    EmailDTO toDTO(EmailDetails emailDetails);

    @Named("objectIdToString")
    default String objectIdToString(ObjectId objectId) {
        return objectId != null ? objectId.toHexString() : null;
    }

    @Named("stringToObjectId")
    default ObjectId stringToObjectId(String id) {
        return id != null ? new ObjectId(id) : null;
    }

    @Named("stringToDate")
    default Date stringToDate(String dateString) {
        try {
            if (dateString == null || dateString.trim().isEmpty()) {
                return new Date();
            }
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return formatter.parse(dateString);
        } catch (Exception e) {
            return new Date();
        }
    }

    @Named("dateToString")
    default String dateToString(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return formatter.format(date);

    }

    @Named("stringToBoolean")
    default Boolean stringToBoolean(String value) {
        if (value == null) return false;
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes") || value.equals("1");
    }

    @Named("booleanToString")
    default String booleanToString(Boolean value) {
        return value != null ? value.toString() : "false";
    }
}
