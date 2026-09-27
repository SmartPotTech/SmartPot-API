package app.smartpot.api.crops.model.dto;

import app.smartpot.api.crops.model.entity.CropHealth;
import app.smartpot.api.readings.model.dto.ReadingResponse;

import java.time.Instant;

public record CropResponse(
        String id,
        String name,
        String type,
        String kind,
        String form,
        boolean automationEnabled,
        DeviceStatus device,
        CropHealth health,
        ReadingResponse latestReading,
        Instant createdAt
) {

    public record DeviceStatus(boolean online, Instant lastSeenAt, Instant keyRotatedAt) {
    }
}
