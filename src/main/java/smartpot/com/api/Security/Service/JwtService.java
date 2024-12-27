package smartpot.com.api.Security.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import smartpot.com.api.Users.Mapper.MUser;
import smartpot.com.api.Users.Model.DAO.Repository.RUser;
import smartpot.com.api.Users.Model.DAO.Service.SUser;
import smartpot.com.api.Users.Model.DAO.Service.SUserI;
import smartpot.com.api.Users.Model.DTO.UserDTO;
import smartpot.com.api.Users.Validation.VUserI;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class JwtService implements JwtServiceI {

    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    @Value("${application.security.jwt.expiration}")
    private long expiration;

    private final SUserI serviceUser;

    /**
     * Constructor que inyecta las dependencias del servicio.
     *
     * @param serviceUser servicio que maneja las operaciones de base de datos.
     */
    @Autowired
    public JwtService(SUserI serviceUser) {
        this.serviceUser = serviceUser;
    }

    @Override
    public String login(UserDTO reqUser) throws Exception {
        return Optional.of(serviceUser.getUserByEmail(reqUser.getEmail()))
                .filter( userDTO -> new BCryptPasswordEncoder().matches(reqUser.getPassword(), userDTO.getPassword()))
                .map(validUser -> generateToken(validUser.getId(), validUser.getEmail()))
                .orElseThrow(() -> new Exception("Credenciales Invalidas"));

    }


    private String generateToken(String id, String email) {
        // TODO: Refine token (email != subject)
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", id);
        claims.put("email", email);
        // claims.put("roles", user.getAuthorities());
        return createToken(claims, email);
        // createToken (CustomClaims, subjectClaim)
    }

    @Override
    public Boolean validateToken(String token, UserDetails userDetails) {
        Date expirationDate = extractExpiration(token);
        if (expirationDate.before(new Date())) {
            return false;
        }
        String username = extractUsername(token);
        return userDetails.getUsername().equals(username) && !expirationDate.before(new Date());
    }

    private String createToken(Map<String, Object> claims, String username) {
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignKey())
                //.signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Key getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    @Override
    public Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    @Override
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    @Override
    public String extractEmail(String token) {
        return extractAllClaims(token).get("email", String.class);
    }

}