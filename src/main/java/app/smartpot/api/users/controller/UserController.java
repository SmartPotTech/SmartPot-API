package app.smartpot.api.users.controller;

import app.smartpot.api.users.mapper.UserMapper;
import app.smartpot.api.users.model.dto.ChangePasswordRequest;
import app.smartpot.api.users.model.dto.UpdateProfileRequest;
import app.smartpot.api.users.model.dto.UserResponse;
import app.smartpot.api.users.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me")
@Tag(name = "Usuarios", description = "Perfil de la cuenta autenticada")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Obtener mi perfil")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserMapper.toResponse(userService.getById(jwt.getSubject()));
    }

    @PutMapping
    @Operation(summary = "Actualizar nombre y apellido")
    public UserResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return UserMapper.toResponse(userService.updateProfile(jwt.getSubject(), request));
    }

    @PutMapping("/password")
    @Operation(summary = "Cambiar la contraseña")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(jwt.getSubject(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @Operation(summary = "Eliminar mi cuenta", description = "Borra la cuenta con sus cultivos, lecturas, comandos y notificaciones")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt) {
        userService.deleteAccount(jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
