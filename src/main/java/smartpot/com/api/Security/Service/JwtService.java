package smartpot.com.api.Security.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.ValidationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import smartpot.com.api.Exception.InvalidTokenException;
import smartpot.com.api.Mail.Model.DTO.EmailDTO;
import smartpot.com.api.Mail.Service.EmailService;
import smartpot.com.api.Mail.Validator.EmailValidatorI;
import smartpot.com.api.Users.Model.DTO.UserDTO;
import smartpot.com.api.Users.Service.SUserI;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.*;

@Service
public class JwtService implements JwtServiceI {

    private static final Log log = LogFactory.getLog(JwtService.class);
    private final SUserI serviceUser;
    private final EmailService emailService;
    private final EmailValidatorI emailValidator;
    private final SecureRandom random = new SecureRandom();

    @Value("${application.security.jwt.secret-key}")
    private String secretKey;
    @Value("${application.security.jwt.expiration}")
    private long expiration;
    @Value("${application.security.aes.key}")
    private String aesKey;

    /**
     * Constructor que inyecta las dependencias del servicio.
     *
     * @param serviceUser servicio que maneja las operaciones de base de datos.
     */
    @Autowired
    public JwtService(SUserI serviceUser, EmailService emailService, EmailValidatorI emailValidator) {
        this.serviceUser = serviceUser;
        this.emailService = emailService;
        this.emailValidator = emailValidator;
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
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new Exception("El encabezado de autorización es inválido. Se esperaba 'Bearer <token>'.");
        }

        String token = authHeader.split(" ")[1];
        token = aes256decrypt(token);
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
                        String token = aes256decrypt(resetToken);
                        if (!extractEmail(token).equals(validUser.getEmail())) {
                            throw new ValidationException("Provided reset token isn't valid");
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

    private String createToken(Map<String, Object> claims, String username) throws Exception {
        String token = Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignKey())
                //.signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
        return aes256Encrypt(token);
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

    private SecretKey getSecretKey() {
        byte[] decoded = Base64.getDecoder().decode(aesKey);
        if (decoded.length != 32) {
            throw new IllegalArgumentException("La clave debe tener 256 bits (32 bytes)");
        }
        return new SecretKeySpec(decoded, "AES");
    }

    private String aes256Encrypt(String data) throws Exception {
        // get salt
        byte[] salt = new byte[8];
        random.nextBytes(salt);
        String saltedData = Base64.getEncoder().encodeToString(salt) + ":" + data;

        byte[] iv = new byte[12];
        random.nextBytes(iv);
        SecretKey key = getSecretKey(); // get encryption key
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));

        byte[] encrypted = cipher.doFinal(saltedData.getBytes());

        byte[] output = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, output, 0, iv.length);
        System.arraycopy(encrypted, 0, output, iv.length, encrypted.length);
        return Base64.getUrlEncoder().encodeToString(output);
    }

    public String aes256decrypt(String encryptedData) throws Exception {
        byte[] decoded = Base64.getUrlDecoder().decode(encryptedData);

        byte[] iv = new byte[12];
        byte[] cipherText = new byte[decoded.length - 12];
        System.arraycopy(decoded, 0, iv, 0, 12);
        System.arraycopy(decoded, 12, cipherText, 0, cipherText.length);

        SecretKey key = getSecretKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));

        String result = new String(cipher.doFinal(cipherText));
        // remove salt
        return result.split(":", 2)[1];
    }
}