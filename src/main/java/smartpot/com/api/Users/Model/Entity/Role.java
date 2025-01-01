package smartpot.com.api.Users.Model.Entity;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum Role {
    USER, ADMIN, SYSTEM;

    /**
     * Obtiene la lista de nombres de roles definidos en el sistema.
     *
     * @return Una lista con los nombres de los roles.
     */
    public static List<String> getRoleNames() {
        return Arrays.stream(Role.values())
                .map(Enum::name)
                .collect(Collectors.toList());
    }
}
