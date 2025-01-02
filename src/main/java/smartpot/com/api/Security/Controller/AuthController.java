package smartpot.com.api.Security.Controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import smartpot.com.api.Exception.InvalidTokenException;
import smartpot.com.api.Responses.ErrorResponse;
import smartpot.com.api.Responses.TokenResponse;
import smartpot.com.api.Security.Service.JwtServiceI;
import smartpot.com.api.Users.Model.DTO.UserDTO;

@RestController
@RequestMapping("/auth")
@Tag(name = "Autentificación", description = "Operaciones relacionadas con autentificación de usuarios")
public class AuthController {

    private final JwtServiceI jwtService;

    @Autowired
    public AuthController(JwtServiceI jwtService) {
        this.jwtService = jwtService;
    }

    // TODO: Handle no allowed method

    @PostMapping("/login")
    @Operation(
            summary = "Iniciar sesión de usuario",
            description = "Permite a un usuario iniciar sesión proporcionando su correo electrónico y contraseña. Retorna un token JWT si las credenciales son válidas.",
            responses = {
                    @ApiResponse(
                            description = "Inicio de sesión exitoso",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenResponse.class))
                    ),
                    @ApiResponse(
                            description = "Credenciales inválidas",
                            responseCode = "400",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
                    )
            }
    )
    public ResponseEntity<?> login(@RequestBody UserDTO reqUser) {
        try {
            return new ResponseEntity<>(new TokenResponse(jwtService.Login(reqUser)), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al iniciar sesion [" + e.getMessage() + "]", HttpStatus.BAD_REQUEST.value()), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/verify")
    @Operation(
            summary = "Verificar token JWT",
            description = "Verifica si el token JWT proporcionado es válido. Si es válido, retorna la información del usuario sin la contraseña.",
            responses = {
                    @ApiResponse(
                            description = "Token válido y usuario encontrado",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class))
                    ),
                    @ApiResponse(
                            description = "Token inválido",
                            responseCode = "418",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
                    ),
                    @ApiResponse(
                            description = "Error al verificar",
                            responseCode = "400",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
                    )
            }
    )
    public ResponseEntity<?> verify(@RequestHeader("Authorization") String authHeader) {
        try {
            return new ResponseEntity<>(jwtService.validateAuthHeader(authHeader), HttpStatus.OK);
        }catch (InvalidTokenException e) {
            return new ResponseEntity<>(new ErrorResponse("Token Invalido [" + e.getMessage() + "]", HttpStatus.I_AM_A_TEAPOT.value()), HttpStatus.I_AM_A_TEAPOT);
        }
        catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al verificar token [" + e.getMessage() + "]", HttpStatus.BAD_REQUEST.value()), HttpStatus.BAD_REQUEST);
        }

    }
}