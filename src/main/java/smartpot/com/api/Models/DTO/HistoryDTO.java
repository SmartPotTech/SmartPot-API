package smartpot.com.api.Models.DTO;

import lombok.Data;

@Data
public class HistoryDTO {
    private MeasuresDTO measures;
    private String crop;

    @Data
    public static class MeasuresDTO {
        private String atmosphere;
        private String brightness;
        private String temperature;
        private String ph;
        private String tds;
        private String humidity;
    }
}
