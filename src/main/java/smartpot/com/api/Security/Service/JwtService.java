package smartpot.com.api.Security.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import smartpot.com.api.Exception.EncryptionException;
import smartpot.com.api.Exception.InvalidTokenException;
import smartpot.com.api.Mail.Model.DTO.EmailDTO;
import smartpot.com.api.Mail.Service.EmailService;
import smartpot.com.api.Mail.Validator.EmailValidatorI;
import smartpot.com.api.Security.Model.DTO.ResetTokenDTO;
import smartpot.com.api.users.model.dto.UserDTO;
import smartpot.com.api.users.service.UserServiceI;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class JwtService implements JwtServiceI {

    private final UserServiceI serviceUser;
    private final EmailService emailService;
    private final EmailValidatorI emailValidator;
    private final EncryptionServiceI encryptionService;

    @Value("${application.security.jwt.secret-key}")
    private String secretKey;
    @Value("${application.security.jwt.expiration}")
    private long expiration;

    /**
     * Constructor que inyecta las dependencias del servicio.
     *
     * @param serviceUser servicio que maneja las operaciones de base de datos.
     */
    @Autowired
    public JwtService(UserServiceI serviceUser, EmailService emailService, EmailValidatorI emailValidator, EncryptionServiceI encryptionService) {
        this.serviceUser = serviceUser;
        this.emailService = emailService;
        this.emailValidator = emailValidator;
        this.encryptionService = encryptionService;
    }

    @Override
    public String Login(UserDTO reqUser) throws Exception {
        return Optional.of(serviceUser.getUserByEmail(reqUser.getEmail()))
                .filter(userDTO -> new BCryptPasswordEncoder().matches(reqUser.getPassword(), userDTO.getPassword()))
                .map(validUser -> {
                    try {
                        return generateToken(validUser.getId(), validUser.getEmail());
                    } catch (Exception e) {
                        throw new ValidationException(e);
                    }
                })
                .orElseThrow(() -> new Exception("Credenciales Invalidas"));
    }

    /**
     * @param reqUser
     * @return
     * @throws Exception
     */
    @Override
    public String Register(UserDTO reqUser) throws Exception {
        return "";
    }

    private String generateToken(String id, String email) throws Exception {
        // TODO: Refine token (email != subject)
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", id);
        claims.put("email", email);
        // claims.put("roles", user.getAuthorities());
        return createToken(claims, email);
        // createToken (CustomClaims, subjectClaim)
    }

    @Override
    public UserDTO validateAuthHeader(String authHeader) throws Exception {
        if (authHeader == null || !authHeader.startsWith("SmartPot-OAuth ")) {
            throw new Exception("El encabezado de autorización es inválido. Se esperaba 'SmartPot-OAuth <token>'.");
        }

        String token = authHeader.split(" ")[1];
        token = encryptionService.decrypt(token);

        String email = extractEmail(token);
        UserDetails user = serviceUser.loadUserByUsername(email);
        if (email == null) {
            throw new InvalidTokenException("Correo no encontrado.");
        }

        if (!validateToken(token, user)) {
            throw new InvalidTokenException("Se ha expirado.");
        }

        UserDTO finalUser = serviceUser.getUserByEmail(email);
        finalUser.setPassword("");
        return finalUser;
    }

    @Override
    public String resetPassword(UserDTO user, String email, String resetToken) throws Exception {
        return Optional.of(serviceUser.getUserByEmail(email))
                .map(validUser -> {
                    try {
                        String decrypted = encryptionService.decrypt(resetToken);
                        ResetTokenDTO resetTokenDTO = ResetTokenDTO.convertToDTO(decrypted);

                        if (!validateResetToken(resetTokenDTO)) {
                            throw new ValidationException("Provided reset token is not valid");
                        }

                        return serviceUser.UpdateUserPassword(validUser, user.getPassword());
                    } catch (Exception e) {
                        throw new ValidationException(e);
                    }
                })
                .map(validUser -> {
                    try {
                        return generateToken(validUser.getId(), validUser.getEmail());
                    } catch (Exception e) {
                        throw new ValidationException(e);
                    }
                })
                .orElseThrow(() -> new Exception("Credenciales Invalidas"));
    }

    @Override
    public Boolean forgotPassword(String email) throws Exception {
        return Optional.of(serviceUser.getUserByEmail(email))
                .map(validUser -> {
                    try {
                        return generateToken(validUser.getId(), validUser.getEmail());
                    } catch (Exception e) {
                        throw new ValidationException(e);
                    }
                })
                .map(token -> new ResetTokenDTO(token, "reset", new Date(System.currentTimeMillis() + expiration)))
                .map(token -> {
                    try {
                        return encryptionService.encrypt(ResetTokenDTO.convertToJson(token));
                    } catch (Exception e) {
                        throw new EncryptionException("Conversion to json or encryption failed: " + e);
                    }
                })
                .map(token -> new EmailDTO(null, email, "Token para recuperar contraseña: " + token, "Recuperar contraseña", "", null, "true"))
                .map(emailService::sendSimpleMail)
                .map(ValidDTO -> {
                    emailValidator.validateId(ValidDTO.getId());
                    emailValidator.validateMsgBody(ValidDTO.getMsgBody());
                    emailValidator.validateAttachment(ValidDTO.getAttachment());
                    emailValidator.validateRecipient(ValidDTO.getRecipient());
                    emailValidator.validateSubject(ValidDTO.getSubject());
                    boolean isValid = emailValidator.isValid();
                    emailValidator.reset();
                    return isValid;
                })
                .orElseThrow(() -> new IllegalStateException("Esta cuenta de correo no esta asociada a un usuario"));
    }

    private Boolean validateToken(String token, UserDetails userDetails) {
        Date expirationDate = extractExpiration(token);
        if (expirationDate.before(new Date())) {
            return false;
        }
        String username = extractUsername(token);
        return userDetails.getUsername().equals(username) && !expirationDate.before(new Date());
    }

    private Boolean validateResetToken(ResetTokenDTO resetTokenDTO) {
        String token = encryptionService.decrypt(resetTokenDTO.getToken());
        if (!validateToken(token, serviceUser.loadUserByUsername(extractEmail(token)))) {
            return false;
        }
        return !resetTokenDTO.getExpiration().before(new Date());
    }

    private String createToken(Map<String, Object> claims, String username) throws Exception {
        String token = Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignKey())
                //.signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
        return encryptionService.encrypt(token);
    }

    private SecretKey getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    private String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    private String extractEmail(String token) {
        return extractAllClaims(token).get("email", String.class);
    }
}