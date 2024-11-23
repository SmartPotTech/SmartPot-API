package smartpot.com.api.Controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import smartpot.com.api.Exception.ApiException;
import smartpot.com.api.Exception.ApiResponse;
import smartpot.com.api.Models.DAO.Service.SUser;
import smartpot.com.api.Models.Entity.User;
import smartpot.com.api.Security.jwt.JwtService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final SUser serviceUser;
    private final JwtService jwtService;

    @Autowired
    public AuthController(final SUser serviceUser, final JwtService jwtService) {
        this.serviceUser = serviceUser;
        this.jwtService = jwtService;
    }

    // TODO: Handle no allowed method

    @PostMapping("/login")
    public String login(@RequestBody User reqUser) {
        User user = serviceUser.getUserByEmail(reqUser.getEmail());
        if (user == null) return "Invalid Credentials";

        boolean passwordMatch = new BCryptPasswordEncoder().matches(reqUser.getPassword(), user.getPassword());
        if (!passwordMatch) return "Invalid Credentials";

        // debug trace
        //System.out.println(reqUser + " - " + user + passwordMatch);

        return jwtService.generateToken(user);
    }

    @GetMapping("/verify")
    public User verify(@RequestHeader("Authorization") String bearerToken) {

        String token = bearerToken.split(" ")[1];
        String email = jwtService.extractEmail(token);
        UserDetails user = serviceUser.loadUserByUsername(email);

        System.out.println("my user ---------------> " + user);

        if (jwtService.validateToken(token, user)) {
            User finalUser = serviceUser.getUserByEmail(email);
            finalUser.setPassword("");
            return finalUser;

        } else {
            throw new ApiException(new ApiResponse(
                    "Token invalido",
                    HttpStatus.I_AM_A_TEAPOT.value()
            ));
        }
    }
}