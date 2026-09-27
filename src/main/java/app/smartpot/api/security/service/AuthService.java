package app.smartpot.api.security.service;

import app.smartpot.api.config.SmartPotProperties;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.mail.service.MailService;
import app.smartpot.api.notifications.model.entity.NotificationType;
import app.smartpot.api.notifications.service.NotificationService;
import app.smartpot.api.security.model.dto.AuthResponse;
import app.smartpot.api.security.model.dto.LoginRequest;
import app.smartpot.api.security.model.dto.RegisterRequest;
import app.smartpot.api.security.model.dto.ResetPasswordRequest;
import app.smartpot.api.security.model.entity.PasswordResetToken;
import app.smartpot.api.security.repository.PasswordResetTokenRepository;
import app.smartpot.api.users.mapper.UserMapper;
import app.smartpot.api.users.model.entity.User;
import app.smartpot.api.users.model.entity.UserRole;
import app.smartpot.api.users.repository.UserRepository;
import app.smartpot.api.users.service.UserService;
import app.smartpot.api.users.validator.PasswordPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Correo o contraseña incorrectos";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MailService mailService;
    private final NotificationService notificationService;
    private final Clock clock;
    private final Duration resetExpiration;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordResetTokenRepository resetTokenRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService, MailService mailService,
                       NotificationService notificationService, Clock clock, SmartPotProperties properties) {
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mailService = mailService;
        this.notificationService = notificationService;
        this.clock = clock;
        this.resetExpiration = properties.security().passwordResetExpiration();
        this.dummyHash = passwordEncoder.encode("smartpot-timing-guard");
    }

    public AuthResponse register(RegisterRequest request) {
        String email = UserService.normalizeEmail(request.email());
        PasswordPolicy.validate(request.password());
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Ya existe una cuenta con ese correo");
        }
        Instant now = clock.instant();
        User user = User.builder()
                .name(request.name().trim())
                .lastName(request.lastName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.USER)
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            user = userRepository.save(user);
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("Ya existe una cuenta con ese correo");
        }
        notificationService.notify(user.getId(), null, NotificationType.INFO, "¡Bienvenido a SmartPot!",
                "Crea tu primer cultivo, real o virtual, para empezar a monitorearlo.");
        mailService.sendWelcome(user);
        log.info("Cuenta registrada: {}", user.getId());
        return toAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        String email = UserService.normalizeEmail(request.email());
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        return toAuthResponse(user);
    }

    public void requestPasswordReset(String rawEmail) {
        String email = UserService.normalizeEmail(rawEmail);
        userRepository.findByEmail(email).ifPresent(user -> {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            Instant now = clock.instant();
            resetTokenRepository.deleteByUserId(user.getId());
            resetTokenRepository.save(PasswordResetToken.builder()
                    .tokenHash(sha256(token))
                    .userId(user.getId())
                    .createdAt(now)
                    .expiresAt(now.plus(resetExpiration))
                    .build());
            mailService.sendPasswordReset(user, token, resetExpiration);
        });
    }

    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = resetTokenRepository.findByTokenHash(sha256(request.token()))
                .filter(token -> token.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> ApiException.badRequest("El enlace de recuperación no es válido o ya venció"));
        PasswordPolicy.validate(request.password());
        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> ApiException.badRequest("El enlace de recuperación no es válido o ya venció"));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setUpdatedAt(clock.instant());
        userRepository.save(user);
        resetTokenRepository.deleteByUserId(user.getId());
        notificationService.notify(user.getId(), null, NotificationType.INFO, "Contraseña actualizada",
                "Tu contraseña se cambió con el enlace de recuperación.");
    }

    private AuthResponse toAuthResponse(User user) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(token.value(), token.expiresAt(), UserMapper.toResponse(user));
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
