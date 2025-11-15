package app.smartpot.api.crops.validator;

import java.util.List;

public interface CropValidator {
    boolean isValid();

    void validateId(String id);

    List<String> getErrors();

    void Reset();

    void validateType(String type);

    void validateStatus(String validStatus);
}
