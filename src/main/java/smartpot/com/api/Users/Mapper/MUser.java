package smartpot.com.api.Users.Mapper;

import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import smartpot.com.api.Users.Model.DTO.UserDTO;
import smartpot.com.api.Users.Model.Entity.User;

@Mapper(componentModel="spring")
public interface MUser {
    MUser INSTANCE = Mappers.getMapper(MUser.class);

    @Mapping(source = "id", target = "id", qualifiedByName = "stringToObjectId")
    @Mapping(source = "password", target = "password", qualifiedByName = "encodePassword")
    public User toEntity(UserDTO userDTO);

    @Mapping(source = "id", target = "id", qualifiedByName = "objectIdToString")
    @Mapping(source = "password", target = "password", qualifiedByName = "decodePassword")
    public UserDTO toDTO(User user);

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
}
