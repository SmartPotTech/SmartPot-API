package smartpot.com.api.Crops.Validator;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;
import smartpot.com.api.Crops.Model.Entity.Status;
import smartpot.com.api.Crops.Model.Entity.Type;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class VCrop implements VCropI {
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
    public VCrop() {
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

        Set<String> validTypes = new HashSet<>(Type.getTypeNames());

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
        Set<String> validStatus = new HashSet<>(Status.getStatusNames());

        if (!validStatus.contains(status)) {
            errors.add("El Estado del cultivo debe ser uno de los siguientes: " + String.join(", ", validStatus));
            valid = false;
        }
    }

}
