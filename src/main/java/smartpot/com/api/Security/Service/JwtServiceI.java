package smartpot.com.api.Security.Service;

import org.springframework.security.core.userdetails.UserDetails;
import smartpot.com.api.Users.Model.Entity.User;

import java.util.Date;

public interface JwtServiceI {
    String generateToken(User user);

    Boolean validateToken(String token, UserDetails userDetails);

    Date extractExpiration(String token);

    String extractUsername(String token);

    String extractEmail(String token);
}
