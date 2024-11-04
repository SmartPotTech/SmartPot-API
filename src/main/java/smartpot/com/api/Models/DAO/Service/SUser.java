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
import smartpot.com.api.utilitys.ErrorResponse;
import smartpot.com.api.utilitys.Exception;
import smartpot.com.api.utilitys.RegexPatterns;

import java.util.List;
import java.util.regex.Pattern;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Service
public class SUser {
    @Autowired
    private RUser repositoryUser;

    public User EditUser(User user) {
        return repositoryUser.save(user);
    }

    public User CreateUser(User user) {
        return repositoryUser.save(user);
    }

    public List<User> getAllUsers() {
        return repositoryUser.findAll();
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
        return repositoryUser.findByRole(role);
    }
    public User updateUser(ObjectId id, User updatedUser) {
        return repositoryUser.updateUser(id, updatedUser);
    }

    public void deleteUser(ObjectId id) {
        repositoryUser.deleteUserById(id);

    }
}
