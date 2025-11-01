package smartpot.com.api.Actuators.Model.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import smartpot.com.api.Actuators.Model.Entity.ActuatorType;

@Data
public class ActuatorDTO {
    @Schema(description = "ID único de la instrucción del actuador, generado automáticamente por la base de datos.",
            example = "676ae2a9b909de5f9607fcb6", hidden = true)
    private String id = null;

    @Schema(description = "La fecha donde se activara el sensor. Si es nulo se asume que se activa al instante",
            example = "2024-12-24 11:34:49")
    private String actingDate = null;

    @Schema(description = "Tiempo de actuación en segundos.",
            example = "300")
    private int performanceTime;

    @Schema(description = "Identificador del cultivo asociado.",
            example = "")
    private String crop;

    @Schema(description = "Tipo de actuador que se desea activar.",
            example = "WATER_PUMP")
    private ActuatorType type;
}
