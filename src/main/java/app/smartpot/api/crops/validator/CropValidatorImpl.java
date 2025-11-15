package app.smartpot.api.crops.validator;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;
import app.smartpot.api.crops.model.entity.CropStatus;
import app.smartpot.api.crops.model.entity.CropType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CropValidatorImpl implements CropValidator {
    /**
     * Indica si la validación fue exitosa.
     */
    public boolean valid;

    /**
     * Lista de errores de validación.
     */
    public List<String> errors;

    /**
     * Constructor de la clase de validación de usuario.
     * Inicializa el estado de validación a "válido" y crea una lista vacía de errores.
     */
    public CropValidatorImpl() {
        this.valid = true;
        this.errors = new ArrayList<>();
    }

    /**
     * Devuelve el estado de validez.
     *
     * @return <code>true</code> si el cultivo es válido, <code>false</code> si hay errores de validación.
     */
    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public void validateId(String id) {
        if (id == null || id.isEmpty()) {
            errors.add("El Id no puede estar vacío");
            valid = false;
        } else if (!ObjectId.isValid(id)) {
            errors.add("El Id debe ser un hexadecimal de 24 caracteres");
            valid = false;
        }
    }

    /**
     * Obtiene la lista de errores encontrados durante la validación.
     *
     * @return Una lista de cadenas con los mensajes de error.
     */
    @Override
    public List<String> getErrors() {
        List<String> currentErrors = errors;
        Reset();
        return currentErrors;
    }

    /**
     * Resetea el estado de la validación, marcando el cultivo como válido y limpiando la lista de errores.
     */
    @Override
    public void Reset() {
        valid = true;
        errors = new ArrayList<>();
    }

    @Override
    public void validateType(String type) {
        if (type == null || type.isEmpty()) {
            errors.add("El tipo de cultivo no puede estar vacío");
            valid = false;
        }

        Set<String> validTypes = Arrays.stream(CropType.values())
                .map(Enum::name).collect(Collectors.toSet());

        if (!validTypes.contains(type)) {
            errors.add("El Tipo de cultivo debe ser uno de los siguientes: " + String.join(", ", validTypes));
            valid = false;
        }
    }

    @Override
    public void validateStatus(String status) {
        if (status == null || status.isEmpty()) {
            errors.add("El Estado del cultivo no puede estar vacío");
            valid = false;
        }
        Set<String> validStatus = Arrays.stream(CropStatus.values())
                .map(Enum::name).collect(Collectors.toSet());

        if (!validStatus.contains(status)) {
            errors.add("El Estado del cultivo debe ser uno de los siguientes: " + String.join(", ", validStatus));
            valid = false;
        }
    }

}
