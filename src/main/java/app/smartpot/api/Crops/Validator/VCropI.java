package app.smartpot.api.Crops.Validator;

import java.util.List;

public interface VCropI {
    boolean isValid();

    void validateId(String id);

    List<String> getErrors();

    void Reset();

    void validateType(String type);

    void validateStatus(String validStatus);
}
