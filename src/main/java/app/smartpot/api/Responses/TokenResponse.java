package app.smartpot.api.Responses;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Builder
@AllArgsConstructor
@Schema(description = "Representa la respuesta que contiene el token JWT generado después de una autenticación exitosa.")
public class TokenResponse {

    @Schema(
            description = "El JSON Web Token (JWT) generado que se utiliza para autenticar al usuario en solicitudes posteriores.",
            example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwgImVtYWlsIjoiam9obi5kb2UuZXhhbXBsZUBleGFtcGxlLmNvbSIsInJvbGUiOiJhZG1pbiIsImlhdCI6MTYxNjIzOTAyMiwiZXhwIjoxNjM2MTI5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
    )
    public String token;
}
