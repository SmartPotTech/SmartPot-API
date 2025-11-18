package app.smartpot.api.commands.model.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import app.smartpot.api.commands.model.entity.CommandStatus;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "comandos")
public class Command {
    /**
     * Representa un comando que puede ser ejecutado en un cultivo específico.
     * * Esta clase contiene información sobre el tipo de comando, su estado,
     * * fechas de creación y ejecución, parámetros adicionales, respuesta
     * * tras la ejecución y el cultivo al que está asociado.
     * TODO: Considerar usar enums para commandType y status para evitar valores incorrectos.
     * ! Asegurarse de que dateExecuted solo se establezca al ejecutar el comando.
     * ? ¿Qué pasa si se envía un comando inválido o mal formado?
     */

    @Id
    @Field("_id")
    private ObjectId id;

    @Field("commandType")
    private String commandType;

    @NotNull
    @Field("actuator")
    private ObjectId actuator;

    @Field("status")
    private CommandStatus status;

    @NotNull(message = "La fecha de creación no puede estar vacía")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    @Field("dateCreated")
    private Date dateCreated;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    @Field("dateExecuted")
    private Date dateExecuted;

    @Field("response")
    private String response;

    @NotNull(message = "El comando debe ejecutarse en un cultivo")

    //@DBRef
    @Field("crop")
    private ObjectId crop;
}

/*
 * Command
 *
 * Descripción: Esta clase representa un comando que puede ser ejecutado en un cultivo específico.
 *
 * Campos:
 * - id: Identificador único del comando.
 * - commandType: Tipo de comando (ej. "ACTIVATE_WATER_PUMP", "TURN_ON_UV_LIGHT").
 * - status: Estado del comando (ej. "PENDING", "EXECUTED", "FAILED").
 * - dateCreated: Fecha de creación del comando (debe ser proporcionada, no puede ser nula).
 * - dateExecuted: Fecha en la que se ejecutó el comando (opcional, puede ser nula si no se ha ejecutado).
 * - response: Respuesta después de la ejecución (ej. éxito, error).
 * - crop: Referencia al cultivo asociado (debe ser proporcionado, no puede ser nulo).
 */