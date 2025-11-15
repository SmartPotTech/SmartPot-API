package app.smartpot.api.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Builder
@AllArgsConstructor
@Schema(description = "Respuesta de error")
public class ErrorResponse {
    @Schema(description = "Mensaje de error", example = "Error al obtener el elemento desde la DB")
    public String message;

    @Schema(description = "Código de error", example = "404", type = "integer")
    public int code;
}
