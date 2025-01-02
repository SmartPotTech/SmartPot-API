package smartpot.com.api.Crops.Model.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;


/**
 * DTO para representar los datos de un cultivo.
 * Este DTO es utilizado para transferir la información de un cultivo
 * entre diferentes capas del sistema, especialmente para su visualización
 * o manipulación en las operaciones CRUD de cultivos.
 */
@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class CropDTO {

    @Schema(description = "ID único del cultivo, generado automáticamente por la base de datos.",
            example = "60b63b8f3e111f8d44d45e72", hidden = true)
    private String id;

    @Schema(description = "Estado actual del cultivo.",
            example = "Perfect_plant")
    private String status;

    @Schema(description = "Tipo de cultivo.",
            example = "TOMATO")
    private String type;

    @Schema(description = "ID del usuario asociado a este cultivo. Este campo se utiliza para asociar un cultivo a un usuario específico del sistema.",
            example = "676ae2a9b909de5f9607fcb6")
    private String user;
}
