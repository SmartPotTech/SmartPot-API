package smartpot.com.api.Controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import smartpot.com.api.Models.DAO.Service.SUser;
import smartpot.com.api.Models.Entity.User;
import smartpot.com.api.Security.jwt.JwtService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    @Autowired
    private SUser serviceUser;

    @Autowired
    private JwtService jwtService;

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
}