package smartpot.com.api.Models.DAO.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import smartpot.com.api.Models.DAO.Repository.RCrop;
import smartpot.com.api.Models.DAO.Repository.RHistory;
import smartpot.com.api.Models.Entity.History;
import smartpot.com.api.Models.Entity.User;
import smartpot.com.api.Validation.Exception.ApiException;
import smartpot.com.api.Validation.Exception.ApiResponse;

import java.util.Date;
import java.util.List;

@Service
public class SHistory {

    @Autowired
    private RHistory repositoryHistory;
    @Autowired
    private SCrop serviceCrop;

    public List<History> getAllHistorys() {
        List<History> historys = repositoryHistory.findAll();
        if (historys == null || historys.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "No se encontro ninguna History en la db",
                    HttpStatus.NOT_FOUND.value()
            ));
        }
        return historys;
    }

    public History Createhistory(History history) {
        validateHistory(history);
        return repositoryHistory.save(history);
    }

    private  void validateHistory(History  history) {
        if (history.getDate() == null) {
            history.setDate(new Date());
        }
        Date currentDate = new Date();
        if(!(history.getDate().compareTo(currentDate) <= 0)) {
            throw new ApiException(new ApiResponse(
                    "La fecha '" + history.getDate() + "' no es válido.",
                    HttpStatus.BAD_REQUEST.value()));
        }
        History.Measures measures = history.getMeasures();
        // Validar rangos estándar
        if (measures.getAtmosphere() < 0 || measures.getAtmosphere() > 1100) {
            throw new ApiException(new ApiResponse(
                    "La atmósfera debe estar entre 0 y 1100 hPa.",
                    HttpStatus.BAD_REQUEST.value()));
        }
        if (measures.getBrightness() < 0 || measures.getBrightness() > 100000) {
            throw new ApiException(new ApiResponse(
                    "El brillo debe estar entre 0 y 100000 lux.",
                    HttpStatus.BAD_REQUEST.value()));
        }
        if (measures.getTemperature() < 0 || measures.getTemperature() > 60) {
            throw new ApiException(new ApiResponse(
                    "La temperatura debe estar entre -50 y 60 °C.",
                    HttpStatus.BAD_REQUEST.value()));
        }
        if (measures.getPh() < 0 || measures.getPh() > 15) {
            throw new ApiException(new ApiResponse(
                    "El pH debe estar entre 0 y 15.",
                    HttpStatus.BAD_REQUEST.value()));
        }
        if (measures.getTds() < 0 || measures.getTds() > 100000) {
            throw new ApiException(new ApiResponse(
                    "El TDS debe estar entre 0 y 100000 ppm.",
                    HttpStatus.BAD_REQUEST.value()));
        }
        if (measures.getHumidity() < 0 || measures.getHumidity() > 100) {
            throw new ApiException(new ApiResponse(
                    "La humedad debe estar entre 0 y 100%.",
                    HttpStatus.BAD_REQUEST.value()));
        }
        serviceCrop.getCropById(history.getId().toString());
    }
      /*  BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String hashedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(hashedPassword);
        try {
            return repositoryUser.save(user);
        } catch (Exception e) {
            log.error("e: ", e);
            throw new ApiException(
                    new ApiResponse("No se pudo crear el usuario.",
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));

    }*/

}
