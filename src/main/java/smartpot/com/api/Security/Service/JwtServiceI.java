package smartpot.com.api.Security.Service;

import org.springframework.security.core.userdetails.UserDetails;
import smartpot.com.api.Users.Model.DTO.UserDTO;

import java.util.Date;

public interface JwtServiceI {
    String login(UserDTO reqUser) throws Exception;

    Boolean validateToken(String token, UserDetails userDetails);

    Date extractExpiration(String token);

    String extractUsername(String token);

    String extractEmail(String token);
}
