package smartpot.com.api.Models.DAO.Service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
import smartpot.com.api.Validation.Exception.ApiResponse;
import smartpot.com.api.Validation.Exception.ApiException;

import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Service
public class SUser implements UserDetailsService {

    @Autowired
    private RUser repositoryUser;

    //Validations

    /* * Patrón para nombres y apellidos (mínimo 4, máximo 15 caracteres) */
    public static final String NAME_PATTERN = "^[a-zA-Z]{4,15}$";

    /* * Patrón para apellidos (mínimo 4, máximo 30 caracteres) */
    public static final String LASTNAME_PATTERN = "^[a-zA-Z]{4,30}$";

    /* * Patrón para correos electrónicos */
    public static final String EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";

    private void ValidationId(String id){
        if(!ObjectId.isValid(id)) {
            throw new ApiException(new ApiResponse(
                    "El usuario con id '"+ id +"' no es válido. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
    }

    private void ValidationName(String name){
        if (!Pattern.matches(NAME_PATTERN, name)) {
            throw new ApiException(new ApiResponse(
                    "El nombre '" + name + "' no es válido. Debe tener entre 4 y 15 caracteres y solo letras.",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
    }

    private void ValidationLastname(String lastname){
        if (!Pattern.matches(LASTNAME_PATTERN,lastname)) {
            throw new ApiException(new ApiResponse(
                    "El apellido '" + lastname + "' no es valido. El apellido debe tener entre 4 y 30 caracteres",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
    }

    private void ValidationEmail(String email){
        if (!Pattern.matches(EMAIL_PATTERN, email)) {
            throw new ApiException(new ApiResponse(
                    "El usuario con correo electrónico '" + email + "' no es válido. Asegúrate de que sigue el formato correcto.",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
    }

    private void ValidationPassword(String password){

    }

    private void ValidationRole(String role){
        if (role == null || role.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "El rol no puede estar vacío",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
        boolean isValidRole = Stream.of(Role.values())
                .anyMatch(r -> r.name().equalsIgnoreCase(role));

        if (!isValidRole) {
            throw new ApiException(new ApiResponse(
                    "El Rol '" + role + "' no válido.",
                    HttpStatus.BAD_REQUEST.value()));
        }
    }

    private void isEmailExist(String email){
        if (!repositoryUser.findByEmail(email).isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "El email '" + email + "' ya está en uso.",
                    HttpStatus.CONFLICT.value()
            ));
        }
    }

    public List<User> getAllUsers() {
        List<User> users = repositoryUser.findAll();
        if (users == null || users.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "No se encontro ningun usuario en la db",
                    HttpStatus.NOT_FOUND.value()
            ));
        }
        return users;
    }

    public User CreateUser(User user) {
        ValidationName(user.getName());
        ValidationLastname(user.getLastname());
        ValidationEmail(user.getEmail());
        ValidationPassword(user.getPassword());
        ValidationRole(user.getRole().toString());
        isEmailExist(user.getEmail());
        user.setCreateAt(new Date());
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
     * @throws ApiException Si no se encuentra el usuario con el id proporcionado.
     */
    public User getUserById(String id) {
        ValidationId(id);

        return repositoryUser.findById(new ObjectId(id))
                .orElseThrow(() -> new ApiException(
                        new ApiResponse("El usuario con id '"+ id +"' no fue encontrado.",
                                HttpStatus.NOT_FOUND.value())
                ));
    }

    public User getUserByEmail(String email) {
        ValidationEmail(email);

        List<User> users = repositoryUser.findByEmail(email);
        if (users == null || users.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "No se encontro ningun usuario con el correo electrónico: '" + email + "'.",
                    HttpStatus.NOT_FOUND.value()
            ));
        }

        return users.get(0);
    }

    public List<User> getUsersByFullName(String name, String lastname) {
        ValidationName(name);
        ValidationLastname(lastname);

        List<User> users = repositoryUser.findByFullName(name, lastname);
        if (users == null || users.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "No se encontro ningun usuario con el nombre '"+ name +"' y apellido '" + lastname + "'.",
                    HttpStatus.NOT_FOUND.value()
            ));
        }
        return users;
    }

    public List<User> getUsersByName(String name) {
        ValidationName(name);

        List<User> users = repositoryUser.findByName(name);
        if (users == null || users.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "No se encontro ningun usuario con el nombre '"+ name +"'.",
                    HttpStatus.NOT_FOUND.value()
            ));
        }
        return users;
    }

    public List<User> getUsersByLastname(String lastname) {
        ValidationLastname(lastname);

        List<User> users = repositoryUser.findByLastname(lastname);
        if (users == null || users.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "No se encontro ningun usuario con el apellido '" + lastname + "'.",
                    HttpStatus.NOT_FOUND.value()
            ));
        }
        return users;
    }

    public List<User> getUsersByRole(String role) {
        ValidationRole(role);

        List<User> users = repositoryUser.findByRole(role);

        if (users.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "No se encontraron usuarios con el rol '" + role + "'.",
                    HttpStatus.NOT_FOUND.value()
            ));
        }

        return users;
    }
    public User updateUser(User existingUser, User updatedUser) {
        if (updatedUser.getName() != null) {
            System.out.println("nuevo nombre");
            String name = updatedUser.getName();
            System.out.println("nombre update"+name);
            ValidationName(name);
            existingUser.setName(name);
            System.out.println("nombre setado"+existingUser.getName());
        }

        if (updatedUser.getLastname() != null) {
            String lastname = updatedUser.getLastname();
            ValidationLastname(lastname);
            existingUser.setLastname(lastname);
        }

        if (updatedUser.getEmail() != null) {
            String email = updatedUser.getEmail();
            ValidationEmail(email);
            isEmailExist(email);
            existingUser.setEmail(updatedUser.getEmail());
        }

        if (updatedUser.getRole() != null) {
            String role = updatedUser.getRole().toString();
            ValidationRole(role);
            existingUser.setRole(updatedUser.getRole());
        }

        if (updatedUser.getPassword() != null && !updatedUser.getPassword().isEmpty()) {
            if (!new BCryptPasswordEncoder().matches(updatedUser.getPassword(), existingUser.getPassword())) {
                existingUser.setPassword(new BCryptPasswordEncoder().encode(updatedUser.getPassword()));
            }
        }

        return repositoryUser.save(existingUser);
    }

    public ResponseEntity<ApiResponse> deleteUser(User existingUser) {
        try {
            repositoryUser.deleteUserById(existingUser.getId());
            return ResponseEntity.status(HttpStatus.OK.value()).body(
                    new ApiResponse("El usuario con ID '" + existingUser.getId() + "' fue eliminado.",
                            HttpStatus.OK.value())
            );
        } catch (Exception e) {
            log.error("e: ", e);
            throw new ApiException(
                    new ApiResponse("No se pudo eliminar el usuario con ID '" + existingUser.getId() + "'.",
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user  = repositoryUser.findByEmail(username).get(0);
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
