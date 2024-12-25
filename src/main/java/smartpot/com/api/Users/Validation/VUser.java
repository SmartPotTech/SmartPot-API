package smartpot.com.api.Users.Validation;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;
import smartpot.com.api.Users.Model.Entity.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static smartpot.com.api.Users.Validation.UserRegex.*;

@Component
public class VUser {
    public boolean valid;
    public List<String> errors;

    public VUser() {
        this.valid = true;
        this.errors = new ArrayList<>();
    }

    public void validateId(String id) {
        if (id == null || id.isEmpty()) {
            errors.add("El Id no puede estar vació");
            valid = false;
        } else if (!ObjectId.isValid(id)) {
            errors.add("El Id debe ser un hexadecimal de 24 caracteres");
            valid = false;
        }

    }

    public void Reset() {
        valid = true;
        errors = new ArrayList<>();
    }

    public void validateName(String name) {
        if (name == null || !Pattern.matches(NAME_PATTERN, name)) {
            valid = false;
            errors.add("El nombre debe tener entre 4 y 15 caracteres y solo letras");
        }
    }

    public void validateLastname(String lastname) {
        if (lastname == null || !Pattern.matches(LASTNAME_PATTERN, lastname)) {
            valid = false;
            errors.add("El apellido debe tener entre 4 y 30 caracteres");
        }
    }

    public void validateEmail(String email) {
        if (email == null || !Pattern.matches(EMAIL_PATTERN, email)) {
            valid = false;
            errors.add("El correo electrónico no es válido");
        }
    }

    public void validatePassword(String password) {
        if (password == null || !Pattern.matches(PASSWORD_PATTERN, password)) {
            valid = false;
            errors.add("La contraseña debe tener al menos 8 caracteres, una letra mayúscula, una letra minúscula, un número y un carácter especial");
        }
    }

    public void validateRole(String role) {
        if (role == null || role.isEmpty()) {
            errors.add("El rol no puede estar vacío");
            valid = false;
        } else {
            boolean isValidRole = false;
            for (String validRole : getRoleNames()) {
                if (validRole.matches(role)) {
                    isValidRole = true;
                    break;
                }
            }

            if (!isValidRole) {
                errors.add("El Rol debe ser uno de los siguientes: " + String.join(", ", getRoleNames()));
                valid = false;
            }
        }
    }

    private List<String> getRoleNames() {
        List<String> roleNames = new ArrayList<>();
        for (Role role : Role.values()) {
            roleNames.add(role.name());
        }
        return roleNames;
    }
}
