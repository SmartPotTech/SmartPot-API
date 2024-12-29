package smartpot.com.api.Crops.Validation;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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

    }

    /**
     * Obtiene la lista de errores encontrados durante la validación.
     *
     * @return Una lista de cadenas con los mensajes de error.
     */
    @Override
    public List<String> getErrors() {
        return errors;
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

    }

    @Override
    public void validateStatus(String validStatus) {

    }

    @Override
    public void validateUser(String user) {

    }
}
