package app.smartpot.api.crops.mapper;

import app.smartpot.api.crops.model.dto.CropResponse;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.model.entity.CropForm;
import app.smartpot.api.crops.model.entity.CropKind;
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
        String kind = (crop.getKind() == null ? CropKind.REAL : crop.getKind()).name();
        String form = (crop.getForm() == null ? CropForm.POT : crop.getForm()).name();
        return new CropResponse(crop.getId(), crop.getName(), crop.getType().name(), kind, form, crop.getPlacement(),
                crop.isAutomationEnabled(), deviceStatus, crop.getHealth(), ReadingMapper.toResponse(latestReading), crop.getCreatedAt());
    }
}
