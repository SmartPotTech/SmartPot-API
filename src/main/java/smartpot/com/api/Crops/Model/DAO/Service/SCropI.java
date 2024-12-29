package smartpot.com.api.Crops.Model.DAO.Service;

import smartpot.com.api.Crops.Model.DTO.CropDTO;

import java.util.List;

public interface SCropI {

    List<CropDTO> getAllCrops() throws Exception;

    CropDTO getCropById(String id) throws Exception;

    List<CropDTO> getCropsByUser(String id) throws Exception;

    List<CropDTO> getCropsByType(String type) throws Exception;

    long countCropsByUser(String id) throws Exception;

    List<CropDTO> getCropsByStatus(String status) throws Exception;

    CropDTO createCrop(CropDTO newCropDto) throws Exception;

    CropDTO updatedCrop(String id, CropDTO cropDto) throws Exception;

    String deleteCrop(String id) throws Exception;
}
