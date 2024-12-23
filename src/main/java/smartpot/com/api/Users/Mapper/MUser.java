package smartpot.com.api.Users.Mapper;

import org.mapstruct.Mapper;
import smartpot.com.api.Users.Model.DTO.UserDTO;
import smartpot.com.api.Users.Model.Entity.User;

@Mapper(componentModel="spring")
public interface MUser {
    /**
     * Convierte un UserDTO a una entidad User.
     *
     * @param userDTO El DTO del usuario.
     * @return La entidad User.
     */
    public User toEntity(UserDTO userDTO);

    /**
     * Convierte una entidad User a un UserDTO.
     *
     * @param user La entidad User.
     * @return El DTO del usuario.
     */
    public UserDTO toDTO(User user);
}
