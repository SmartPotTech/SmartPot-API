package smartpot.com.api.Models.DTO;

import lombok.Data;

@Data
public class RecordDTO {
    private MeasuresDTO measures;
    private String crop;
}
