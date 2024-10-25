package smartpot.com.api.Controllers;

import smartpot.com.api.Models.DAO.RUser;
import smartpot.com.api.Models.Entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/Usuarios")
public class UserController {

    @Autowired
    private RUser repositoryUser;

    @GetMapping
    public List<User> getAllUsers() {
        return repositoryUser.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUser(@PathVariable String id) {
        return repositoryUser.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RuntimeException("Usuario con id " + id + " no encontrado"));
    }

    @PostMapping
    public User createUser(@RequestBody User newUser) {
        return repositoryUser.save(newUser);
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable String id, @RequestBody User userDetails) {
        return repositoryUser.findById(id)
                .map(user -> {
                    user.setName(userDetails.getName());
                    user.setPassword(userDetails.getPassword());
                    user.setEmail(userDetails.getEmail());
                    user.setRole(userDetails.getRole());
                    User updatedUser = repositoryUser.save(user);
                    return ResponseEntity.ok(updatedUser);
                })
                .orElseThrow(() -> new RuntimeException("Usuario con id " + id + " no encontrado"));
    }
}
