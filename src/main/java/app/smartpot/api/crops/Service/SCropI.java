package app.smartpot.api.crops.Service;

import app.smartpot.api.crops.model.DTO.CropDTO;

import java.util.List;

public interface SCropI {

    List<CropDTO> getAllCrops() throws Exception;

    CropDTO getCropById(String id) throws Exception;

    List<CropDTO> getCropsByUser(String id) throws Exception;

    long countCropsByUser(String id) throws Exception;

    List<CropDTO> getCropsByType(String type) throws Exception;

    List<String> getAllTypes() throws Exception;

    List<CropDTO> getCropsByStatus(String status) throws Exception;

    List<String> getAllStatus() throws Exception;

    CropDTO createCrop(CropDTO newCropDto) throws Exception;

    CropDTO updatedCrop(String id, CropDTO cropDto) throws Exception;

    String deleteCrop(String id) throws Exception;

}
