package app.smartpot.api.crops.mapper;

import app.smartpot.api.crops.model.dto.CropResponse;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.model.entity.Device;
import app.smartpot.api.readings.mapper.ReadingMapper;
import app.smartpot.api.readings.model.entity.Reading;

public final class CropMapper {

    private CropMapper() {
    }

    public static CropResponse toResponse(Crop crop, Reading latestReading) {
        Device device = crop.getDevice();
        CropResponse.DeviceStatus deviceStatus = device == null
                ? new CropResponse.DeviceStatus(false, null, null)
                : new CropResponse.DeviceStatus(device.isOnline(), device.getLastSeenAt(), device.getKeyRotatedAt());
        return new CropResponse(crop.getId(), crop.getName(), crop.getType().name(), crop.isAutomationEnabled(),
                deviceStatus, crop.getHealth(), ReadingMapper.toResponse(latestReading), crop.getCreatedAt());
    }
}
