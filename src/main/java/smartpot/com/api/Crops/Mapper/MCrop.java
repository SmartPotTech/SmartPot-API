package smartpot.com.api.Crops.Mapper;

import org.mapstruct.Mapper;
import smartpot.com.api.Crops.Model.DTO.CropDTO;
import smartpot.com.api.Crops.Model.Entity.Crop;

@Mapper(componentModel="spring")
public interface MCrop {
    public Crop toEntity(CropDTO cropDTO);
    public CropDTO toDTO(Crop crop);
}
