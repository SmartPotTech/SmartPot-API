package smartpot.com.api.Users.Mapper;

import org.springframework.stereotype.Component;
import smartpot.com.api.Users.Model.DTO.UserDTO;
import smartpot.com.api.Users.Model.Entity.User;

@Component
public class UserMapper {
    /**
     * Convierte un UserDTO a una entidad User.
     *
     * @param userDTO El DTO del usuario.
     * @return La entidad User.
     */
    public User toEntity(UserDTO userDTO) {
        User user = new User();
        user.setName(userDTO.getName());
        user.setPassword(userDTO.getPassword());
        user.setEmail(userDTO.getEmail());
        return user;
    }

    /**
     * Convierte una entidad User a un UserDTO.
     *
     * @param user La entidad User.
     * @return El DTO del usuario.
     */
    public UserDTO toDTO(User user) {
        UserDTO userDTO = new UserDTO();
        userDTO.setName(user.getName());
        userDTO.setPassword(user.getPassword());
        userDTO.setEmail(user.getEmail());
        return userDTO;
    }
}
