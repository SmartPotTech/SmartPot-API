package smartpot.com.api.Records.Model.Entity;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Measures implements Serializable {

    @Positive(message = "La atmósfera debe ser un valor positivo")
    @Field("atmosphere")
    private Double atmosphere;

    @Positive(message = "El brillo debe ser un valor positivo")
    @Field("brightness")
    private Double brightness;

    @Positive(message = "La temperatura debe ser un valor positivo")
    @Field("temperature")
    private Double temperature;

    @Positive(message = "El pH debe ser un valor positivo")
    @Field("ph")
    private Double ph;

    @Positive(message = "El TDS debe ser un valor positivo")
    @Field("tds")
    private Double tds;

    @Positive(message = "La humedad debe ser un valor positivo")
    @Field("humidity")
    private Double humidity;
}
