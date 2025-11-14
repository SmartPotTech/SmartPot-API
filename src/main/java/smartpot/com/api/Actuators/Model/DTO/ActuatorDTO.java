package smartpot.com.api.Actuators.Model.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import smartpot.com.api.Actuators.Model.Entity.ActuatorType;

@Data
public class ActuatorDTO {
    @Schema(description = "ID único de la instrucción del actuador, generado automáticamente por la base de datos.",
            example = "676ae2a9b909de5f9607fcb6", hidden = true)
    private String id = null;

    @Schema(description = "Identificador del cultivo asociado.",
            example = "")
    private String crop;

    @Schema(description = "Tipo de actuador que se desea activar.",
            example = "WATER_PUMP")
    private ActuatorType type;
}
