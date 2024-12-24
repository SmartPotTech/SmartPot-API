package smartpot.com.api.Users.Model.DAO.Service;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetailsService;
import smartpot.com.api.Exception.ApiResponse;
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

    User getUserById(String id) throws Exception;

    User getUserByEmail(String email);

    List<User> getUsersByFullName(String name, String lastname);

    List<User> getUsersByName(String name);

    List<User> getUsersByLastname(String lastname);

    List<User> getUsersByRole(String role);

    User updateUser(User existingUser, UserDTO updatedUser);

    ResponseEntity<ApiResponse> deleteUser(User existingUser);
}
