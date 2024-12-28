package smartpot.com.api.Security.Service;

import org.springframework.security.core.userdetails.UserDetails;
import smartpot.com.api.Users.Model.DTO.UserDTO;

import java.util.Date;

public interface JwtServiceI {
    String Login(UserDTO reqUser) throws Exception;

    UserDTO validateAuthHeader(String token) throws Exception;

}
