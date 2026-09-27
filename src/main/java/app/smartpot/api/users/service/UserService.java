package app.smartpot.api.users.service;

import app.smartpot.api.channels.service.ChannelService;
import app.smartpot.api.crops.service.CropService;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.notifications.service.NotificationService;
import app.smartpot.api.security.repository.PasswordResetTokenRepository;
import app.smartpot.api.users.model.dto.ChangePasswordRequest;
import app.smartpot.api.users.model.dto.UpdateProfileRequest;
import app.smartpot.api.users.model.entity.User;
import app.smartpot.api.users.repository.UserRepository;
import app.smartpot.api.users.validator.PasswordPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Locale;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final CropService cropService;
    private final NotificationService notificationService;
    private final ChannelService channelService;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(UserRepository userRepository, CropService cropService, NotificationService notificationService,
                       ChannelService channelService, PasswordResetTokenRepository resetTokenRepository,
                       PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.cropService = cropService;
        this.notificationService = notificationService;
        this.channelService = channelService;
        this.resetTokenRepository = resetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public User getById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Tu cuenta ya no existe. Inicia sesión de nuevo"));
    }

    public User updateProfile(String userId, UpdateProfileRequest request) {
        User user = getById(userId);
        user.setName(request.name().trim());
        user.setLastName(request.lastName().trim());
        user.setUpdatedAt(clock.instant());
        return userRepository.save(user);
    }

    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = getById(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("La contraseña actual no es correcta");
        }
        PasswordPolicy.validate(request.newPassword());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(clock.instant());
        userRepository.save(user);
    }

    public void deleteAccount(String userId) {
        User user = getById(userId);
        cropService.deleteAllOwnedBy(userId);
        notificationService.deleteAllForUser(userId);
        channelService.deleteAllForUser(userId);
        resetTokenRepository.deleteByUserId(userId);
        userRepository.delete(user);
        log.info("Cuenta {} eliminada con todos sus datos", userId);
    }
}
