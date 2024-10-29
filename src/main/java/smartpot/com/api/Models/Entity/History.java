package smartpot.com.api.Models.Entity;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "registros")
public class History {
    @Id
    private String id;

    @NotEmpty(message = "La fecha no puede estar vacía")
    private Date date;

    private Measures measures;

    private String cultivation;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Measures {
        private Double atmosphere;
        private Double brightness;
        private Double temperature;
        private Double ph;
        private Double tds;
        private Double humidity;
    }
}

