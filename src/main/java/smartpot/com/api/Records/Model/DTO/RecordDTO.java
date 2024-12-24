package smartpot.com.api.Records.Model.DTO;

import lombok.Data;

@Data
public class RecordDTO {
    private String id;
    private String date;
    private MeasuresDTO measures;
    private String crop;
}
