package smartpot.com.api.Models.DAO.Service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import smartpot.com.api.Models.DAO.Repository.RUser;
import smartpot.com.api.Models.Entity.Role;
import smartpot.com.api.Models.Entity.User;
import smartpot.com.api.Validation.ErrorResponse;
import smartpot.com.api.Validation.Exception;
import smartpot.com.api.Validation.RegexPatterns;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Service
public class SUser implements UserDetailsService {

    @Autowired
    private RUser repositoryUser;

    public List<User> getAllUsers() {
        List<User> users = repositoryUser.findAll();
        if (users == null || users.isEmpty()) {
            throw new Exception(new ErrorResponse(
                    "No se encontro ningun usuario en la db",
                    HttpStatus.NOT_FOUND.value()
            ));
        }
        return users;
    }

    public User EditUser(User user) {
        return repositoryUser.save(user);
    }

    public User CreateUser(User user) {
        if (!user.getName().matches(RegexPatterns.NAME_PATTERN)) {
            throw new Exception(new ErrorResponse(
                    "El nombre '" + user.getName() + "' no es válido. Debe tener entre 4 y 15 caracteres.",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }

        if (!user.getLastname().matches(RegexPatterns.LASTNAME_PATTERN)) {
            throw new Exception(new ErrorResponse(
                    "El apellido '" + user.getLastname() + "' no es válido. Debe tener entre 4 y 30 caracteres.",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }

        if (!user.getEmail().matches(RegexPatterns.EMAIL_PATTERN)) {
            throw new Exception(new ErrorResponse(
                    "El email '" + user.getEmail() + "' no es válido.",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }

        if (repositoryUser.findByEmail(user.getEmail()) != null) {
            throw new Exception(new ErrorResponse(
                    "El email '" + user.getEmail() + "' ya está en uso.",
                    HttpStatus.CONFLICT.value()
            ));
        }

        boolean isValidRole = Stream.of(Role.values())
                .anyMatch(r -> r.name().equalsIgnoreCase(user.getRole().name()));
        if (!isValidRole) {
            throw new Exception(new ErrorResponse(
                    "El Rol '" + user.getRole().name() + "' no válido.",
                    HttpStatus.BAD_REQUEST.value()));
        }

        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String hashedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(hashedPassword);

        return repositoryUser.save(user);
    }

    /**
     * Este metodo maneja el ID como un ObjectId de MongoDB. Si el ID es válido como ObjectId,
     * se utiliza en la búsqueda como tal.
     *
     * @param id El identificador del usuario a buscar. Se recibe como String para evitar errores de conversion.
     * @return El usuario correspondiente al id proporcionado.
     * @throws ResponseStatusException Si el id proporcionado no es válido o no se encuentra el usuario.
     * @throws Exception Si no se encuentra el usuario con el id proporcionado.
     */
    public User getUserById(String id) {
        if(!ObjectId.isValid(id)) {
            throw new Exception(new ErrorResponse(
                    "El usuario con id '"+ id +"' no es válido. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
        return repositoryUser.findById(new ObjectId(id))
                .orElseThrow(() -> new Exception(
                        new ErrorResponse("El usuario con id '"+ id +"' no fue encontrado.",
                                HttpStatus.NOT_FOUND.value())
                ));
    }

    public User getUserByEmail(String email) {
        if (!Pattern.matches(RegexPatterns.EMAIL_PATTERN, email)) {
            throw new Exception(new ErrorResponse(
                    "El usuario con correo electrónico '" + email + "' no es válido. Asegúrate de que sigue el formato correcto.",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }

        List<User> users = repositoryUser.findByEmail(email);
        if (users == null || users.isEmpty()) {
            throw new Exception(new ErrorResponse(
                    "No se encontro ningun usuario con el correo electrónico: '" + email + "'.",
                    HttpStatus.NOT_FOUND.value()
            ));
        }

        return users.get(0);
    }

    public List<User> getUsersByFullName(String name, String lastname) {
        if (!Pattern.matches(RegexPatterns.NAME_PATTERN,name) || !Pattern.matches(RegexPatterns.LASTNAME_PATTERN,lastname)) {
            throw new Exception(new ErrorResponse(
                    "El nombre o apellido no sigue el formato permitido.",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }

        List<User> users = repositoryUser.findByFullName(name, lastname);
        if (users == null || users.isEmpty()) {
            throw new Exception(new ErrorResponse(
                    "No se encontro ningun usuario con el nombre '"+ name +"' y apellido '" + lastname + "'.",
                    HttpStatus.NOT_FOUND.value()
            ));
        }
        return users;
    }

    public List<User> getUsersByName(String name) {
        if (!Pattern.matches(RegexPatterns.NAME_PATTERN, name)) {
            throw new Exception(new ErrorResponse(
                    "El nombre '" + name + "' no es válido. Debe tener entre 4 y 15 caracteres y solo letras.",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }

        List<User> users = repositoryUser.findByName(name);
        if (users == null || users.isEmpty()) {
            throw new Exception(new ErrorResponse(
                    "No se encontro ningun usuario con el nombre '"+ name +"'.",
                    HttpStatus.NOT_FOUND.value()
            ));
        }
        return users;
    }

    public List<User> getUsersByLastname(String lastname) {
        if (!Pattern.matches(RegexPatterns.LASTNAME_PATTERN,lastname)) {
            throw new Exception(new ErrorResponse(
                    "El apellido '" + lastname + "' no es valido. El apellido debe tener entre 4 y 30 caracteres",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }

        List<User> users = repositoryUser.findByLastname(lastname);
        if (users == null || users.isEmpty()) {
            throw new Exception(new ErrorResponse(
                    "No se encontro ningun usuario con el apellido '" + lastname + "'.",
                    HttpStatus.NOT_FOUND.value()
            ));
        }
        return users;
    }

    public List<User> getUsersByRole(String role) {
        if (role == null || role.isEmpty()) {
            throw new Exception(new ErrorResponse(
                    "El rol no puede estar vacío",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
        boolean isValidRole = Stream.of(Role.values())
                .anyMatch(r -> r.name().equalsIgnoreCase(role));

        if (!isValidRole) {
            throw new Exception(new ErrorResponse(
                    "El Rol '" + role + "' no válido.",
                    HttpStatus.BAD_REQUEST.value()));
        }

        List<User> users = repositoryUser.findByRole(role);

        if (users.isEmpty()) {
            throw new Exception(new ErrorResponse(
                    "No se encontraron usuarios con el rol '" + role + "'.",
                    HttpStatus.NOT_FOUND.value()
            ));
        }

        return users;
    }
    public User updateUser(String  id, User updatedUser) {
        if (!ObjectId.isValid(id)) {
            throw new Exception(new ErrorResponse(
                    "El ID '" + id + "' no es válido. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }

        User existingUser = repositoryUser.findById(new ObjectId(id))
                .orElseThrow(() -> new Exception(
                        new ErrorResponse("El usuario con ID '" + id + "' no fue encontrado.",
                                HttpStatus.NOT_FOUND.value())
                ));

        if (!updatedUser.getName().matches(RegexPatterns.NAME_PATTERN)) {
            throw new Exception(new ErrorResponse(
                    "El nombre '" + updatedUser.getName() + "' no es válido.",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }

        if (!updatedUser.getLastname().matches(RegexPatterns.LASTNAME_PATTERN)) {
            throw new Exception(new ErrorResponse(
                    "El apellido '" + updatedUser.getLastname() + "' no es válido.",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }

        if (!updatedUser.getEmail().matches(RegexPatterns.EMAIL_PATTERN)) {
            throw new Exception(new ErrorResponse(
                    "El correo electrónico '" + updatedUser.getEmail() + "' no es válido.",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }

        if (repositoryUser.findByEmail(updatedUser.getEmail()) != null &&
                !existingUser.getEmail().equals(updatedUser.getEmail())) {
            throw new Exception(new ErrorResponse(
                    "El correo electrónico '" + updatedUser.getEmail() + "' ya está en uso.",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }

        if (updatedUser.getRole() == null || !Stream.of(Role.values()).anyMatch(r -> r.equals(updatedUser.getRole()))) {
            throw new Exception(new ErrorResponse(
                    "El rol '" + updatedUser.getRole() + "' no es válido.",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }

        if (updatedUser.getPassword() != null && !updatedUser.getPassword().isEmpty()) {
            if (!new BCryptPasswordEncoder().matches(updatedUser.getPassword(), existingUser.getPassword())) {
                updatedUser.setPassword(new BCryptPasswordEncoder().encode(updatedUser.getPassword()));
            }
        } else {
            updatedUser.setPassword(existingUser.getPassword());
        }

        updatedUser.setCreateAt(existingUser.getCreateAt());

        updatedUser.setId(existingUser.getId());
        return repositoryUser.updateUser(new ObjectId(id), updatedUser);
    }

    public void deleteUser(String id) {
        if (!ObjectId.isValid(id)) {
            throw new Exception(new ErrorResponse(
                    "El ID '" + id + "' no es válido. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }

        User existingUser = repositoryUser.findById(new ObjectId(id))
                .orElseThrow(() -> new Exception(
                        new ErrorResponse("El usuario con ID '" + id + "' no fue encontrado.",
                                HttpStatus.NOT_FOUND.value())
                ));

        repositoryUser.deleteUserById(new ObjectId(id));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user  = (User) repositoryUser.findByEmail(username);
        if (user != null) {
            return org.springframework.security.core.userdetails.User.builder()
                    .username(user.getEmail())
                    .password(user.getPassword())
                    .build();
        } else {
            throw new UsernameNotFoundException(username);
        }
    }
}
