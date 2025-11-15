package app.smartpot.api.security.service;

import app.smartpot.api.users.model.dto.UserDTO;

public interface JwtService {
    String Login(UserDTO reqUser) throws Exception;

    String Register(UserDTO reqUser) throws Exception;

    UserDTO validateAuthHeader(String token) throws Exception;

    String resetPassword(UserDTO reqUser, String email, String resetToken) throws Exception;

    Boolean forgotPassword(String email) throws Exception;
}
