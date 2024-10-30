package smartpot.com.api.Models.Entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "registros")
public class History implements Serializable {
    @Id
    private String id;

    @NotNull(message = "La fecha no puede estar vacía")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date date;

    private Measures measures;

    @DBRef
    private Crop crop;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Measures implements Serializable{
        private Double atmosphere;
        private Double brightness;
        private Double temperature;
        private Double ph;
        private Double tds;
        private Double humidity;
    }
}

