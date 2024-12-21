package smartpot.com.api.Crops.Model.DAO.Service;

import org.springframework.http.ResponseEntity;
import smartpot.com.api.Crops.Model.DTO.CropDTO;
import smartpot.com.api.Crops.Model.Entity.Crop;
import smartpot.com.api.Exception.ApiResponse;

import java.util.List;

public interface SCropI {
    Crop getCropById(String id);

    List<Crop> getCrops();

    List<Crop> getCropsByUser(String id);

    List<Crop> getCropsByType(String type);

    long countCropsByUser(String id);

    List<Crop> getCropsByStatus(String status);

    Crop createCrop(CropDTO newCropDto);

    Crop updatedCrop(String id, CropDTO cropDto);

    /* public void deleteCrop(String id) {
            if (!ObjectId.isValid(id)) {
                throw new ApiException(new ApiResponse(
                        "El ID '" + id + "' no es válido. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                        HttpStatus.BAD_REQUEST.value()
                ));
            }
            Crop existingCrop = repositoryCrop.findById(new ObjectId(id))
                    .orElseThrow(() -> new ApiException(
                            new ApiResponse("El usuario con ID '" + id + "' no fue encontrado.",
                                    HttpStatus.NOT_FOUND.value())
                    ));

            repositoryCrop.deleteById(existingCrop.getId());
        }*/
    ResponseEntity<ApiResponse> deleteCrop(Crop existingCrop);
}
