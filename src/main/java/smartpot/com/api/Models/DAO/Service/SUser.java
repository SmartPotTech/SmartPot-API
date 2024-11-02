package smartpot.com.api.Models.DAO.Service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import smartpot.com.api.Models.DAO.Repository.RUser;
import smartpot.com.api.Models.Entity.User;
import smartpot.com.api.utilitys.Exception;

import java.util.List;
import java.util.Optional;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Service
public class SUser {
    @Autowired
    private RUser repositoryUser;

    public User saveUser(User user) {
        return repositoryUser.save(user);
    }

    public List<User> getAllUsers() {
        return repositoryUser.findAll();
    }

    public Optional<User> getUserById(ObjectId id) {
        return repositoryUser.findById(id);
    }

    public List<User> getUsersByEmail(String email) {
        return repositoryUser.findByEmail(email);
    }

    public List<User> getUsersByName(String name) {
        return repositoryUser.findByName(name);
    }

    public List<User> getUsersByRole(String role) {
        return repositoryUser.findByRole(role);
    }
    public User updateUser(ObjectId id, User updatedUser) {
        return repositoryUser.updateUser(id, updatedUser);
    }

    public void deleteUser(ObjectId id) {
        repositoryUser.deleteUserById(id);

    }
}
