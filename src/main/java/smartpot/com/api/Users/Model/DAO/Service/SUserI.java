package smartpot.com.api.Users.Model.DAO.Service;

import org.springframework.security.core.userdetails.UserDetailsService;
import smartpot.com.api.Users.Model.DTO.UserDTO;
import smartpot.com.api.Users.Model.Entity.User;

import java.util.List;

public interface SUserI extends UserDetailsService {
    void ValidationId(String id);

    void ValidationName(String name);

    void ValidationLastname(String lastname);

    void ValidationEmail(String email);

    void ValidationPassword(String password);

    void ValidationRole(String role);

    void isEmailExist(String email);

    List<User> getAllUsers();

    User CreateUser(UserDTO userDTO);

    UserDTO getUserById(String id) throws Exception;

    UserDTO getUserByEmail(String email) throws Exception;

    List<User> getUsersByFullName(String name, String lastname);

    List<UserDTO> getUsersByName(String name) throws Exception;

    List<UserDTO> getUsersByLastname(String lastname) throws Exception;

    List<UserDTO> getUsersByRole(String role) throws Exception;

    User updateUser(User existingUser, UserDTO updatedUser);

    String deleteUser(String id) throws Exception;
}
