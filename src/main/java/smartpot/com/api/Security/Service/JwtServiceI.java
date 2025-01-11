package smartpot.com.api.Security.Service;

import smartpot.com.api.Users.Model.DTO.UserDTO;

public interface JwtServiceI {
    String Login(UserDTO reqUser) throws Exception;

    UserDTO validateAuthHeader(String token) throws Exception;

}
