package smartpot.com.api.Users.Model.DAO.Service;

import org.springframework.security.core.userdetails.UserDetailsService;
import smartpot.com.api.Users.Model.DTO.UserDTO;
import smartpot.com.api.Users.Model.Entity.User;

import java.util.List;

public interface SUserI extends UserDetailsService {
    List<UserDTO> getAllUsers() throws Exception;

    UserDTO CreateUser(UserDTO userDTO) throws Exception;

    UserDTO getUserById(String id) throws Exception;

    UserDTO getUserByEmail(String email) throws Exception;

    List<User> getUsersByFullName(String name, String lastname) throws Exception;

    List<UserDTO> getUsersByName(String name) throws Exception;

    List<UserDTO> getUsersByLastname(String lastname) throws Exception;

    List<UserDTO> getUsersByRole(String role) throws Exception;

    User updateUser(User existingUser, UserDTO updatedUser);

    String deleteUser(String id) throws Exception;
}
