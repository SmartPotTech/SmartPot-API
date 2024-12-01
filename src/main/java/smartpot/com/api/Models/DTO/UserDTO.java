package smartpot.com.api.Models.DTO;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class UserDTO {
    private String name;
    private String lastname;
    private String email;
    private String password;
    private String role;
}
