package app.smartpot.api.users.mapper;

import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import app.smartpot.api.users.model.dto.UserDTO;
import app.smartpot.api.users.model.entity.User;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(source = "id", target = "id", qualifiedByName = "stringToObjectId")
    @Mapping(source = "password", target = "password", qualifiedByName = "encodePassword")
    @Mapping(source = "createAt", target = "createAt", qualifiedByName = "stringToDate")
    User toEntity(UserDTO userDTO);

    @Mapping(source = "id", target = "id", qualifiedByName = "objectIdToString")
    @Mapping(source = "password", target = "password", qualifiedByName = "decodePassword")
    @Mapping(source = "createAt", target = "createAt", qualifiedByName = "dateToString")
    UserDTO toDTO(User user);

    @org.mapstruct.Named("objectIdToString")
    default String objectIdToString(ObjectId objectId) {
        return objectId != null ? objectId.toHexString() : null;
    }

    @org.mapstruct.Named("stringToObjectId")
    default ObjectId stringToObjectId(String id) {
        return id != null ? new ObjectId(id) : null;
    }

    @org.mapstruct.Named("encodePassword")
    default String encodePassword(String password) {
        if (password == null || password.isEmpty()) {
            return null;
        }
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        return passwordEncoder.encode(password);
    }

    @org.mapstruct.Named("decodePassword")
    default String decodePassword(String encodedPassword) {
        return encodedPassword;
    }

    @org.mapstruct.Named("stringToDate")
    default Date stringToDate(String dateString) throws ParseException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return formatter.parse(dateString);
    }

    @org.mapstruct.Named("dateToString")
    default String dateToString(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return formatter.format(date);

    }
}
