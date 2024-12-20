package smartpot.com.api.Records.Model.DTO;

import lombok.Data;

@Data
public class RecordDTO {
    private MeasuresDTO measures;
    private String crop;
}
