package smartpot.com.api.Models.DAO.Service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import smartpot.com.api.Models.DAO.Repository.RCrop;
import smartpot.com.api.Models.Entity.Crop;
import smartpot.com.api.Models.Entity.User;

import java.util.List;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Service
public class SCrop {
    @Autowired
    private RCrop repositoryCrop;

    public List<Crop> getCropsByUser(User user) {
        return repositoryCrop.findByUser(user);
    }

    public List<Crop> getCropsByType(String type) {
        return repositoryCrop.findByType(type);
    }

    public long countCropsByUser(User user) {
        return repositoryCrop.countByUser(user);
    }

    public List<Crop> getCropsByStatus(String status) {
        return repositoryCrop.findByStatus(status);
    }


    public Crop createCrop(Crop newCrop) {
        return repositoryCrop.save(newCrop);
    }
}
