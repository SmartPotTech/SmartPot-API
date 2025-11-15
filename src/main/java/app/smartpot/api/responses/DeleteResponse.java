package app.smartpot.api.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Builder
@AllArgsConstructor
@Schema(description = "Representa la respuesta después de una operación de eliminación exitosa.")
public class DeleteResponse {

    @Schema(
            description = "Mensaje que indica el estado de la operación de eliminación.",
            example = "El recurso ha sido eliminado exitosamente."
    )
    public String message;
}