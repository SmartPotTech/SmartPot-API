package app.smartpot.api.records.service;

import org.springframework.http.ResponseEntity;
import app.smartpot.api.exception.ApiResponse;
import app.smartpot.api.records.model.dto.CropRecordDTO;
import app.smartpot.api.records.model.dto.RecordDTO;
import app.smartpot.api.records.model.entity.DateRange;
import app.smartpot.api.records.model.entity.History;

import java.util.List;

public interface RecordService {
    List<History> getAllHistories();

    History getHistoryById(String id);

    List<History> getByCrop(String cropId) throws Exception;

    List<History> getHistoriesByCropAndDateBetween(String cropId, DateRange ranges);

    List<CropRecordDTO> getByUser(String id) throws Exception;

    History Createhistory(RecordDTO recordDTO) throws Exception;

    History updatedHistory(History existingHistory, RecordDTO updateHistory);

    ResponseEntity<ApiResponse> deleteHistory(History existingHistory);
}
