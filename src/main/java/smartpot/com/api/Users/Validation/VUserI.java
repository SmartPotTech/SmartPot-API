package smartpot.com.api.Users.Validation;

import java.util.List;

public interface VUserI {

    void validateId(String id);

    void Reset();

    void validateName(String name);

    void validateLastname(String lastname);

    void validateEmail(String email);

    void validatePassword(String password);

    void validateRole(String role);

    boolean isValid();

    List<String> getErrors();

}
