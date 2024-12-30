package smartpot.com.api.Crops.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import smartpot.com.api.Crops.Model.DAO.Service.SCropI;
import smartpot.com.api.Crops.Model.DTO.CropDTO;
import smartpot.com.api.Crops.Model.Entity.Status;
import smartpot.com.api.Crops.Model.Entity.Type;

import java.util.Arrays;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@WebMvcTest(CropController.class)
@ComponentScan(basePackages = "smartpot.com.api.Crops")
class CropControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Mock
    private SCropI serviceCrop;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Aquí se puede inicializar cualquier cosa antes de cada prueba, si es necesario
    }

    @AfterEach
    void tearDown() {
        // Aquí se puede limpiar lo que se necesite después de cada prueba, si es necesario
    }

    // Test para POST /Crops/Create
    @Test
    void createCrop() throws Exception {
        // Usamos un valor válido de los enums Status y Type
        CropDTO newCrop = new CropDTO("60b63b8f3e111f8d44d45e72", Status.Perfect_plant.name(), Type.TOMATO.name(), "676ae2a9b909de5f9607fcb6");

        // Mockeamos la respuesta del servicio
        when(serviceCrop.createCrop(any(CropDTO.class))).thenReturn(newCrop);

        mockMvc.perform(post("/Crops/Create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newCrop)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("60b63b8f3e111f8d44d45e72"))
                .andExpect(jsonPath("$.status").value("Perfect_plant"))
                .andExpect(jsonPath("$.type").value("TOMATO"))
                .andExpect(jsonPath("$.user").value("676ae2a9b909de5f9607fcb6"));
    }

    // Test para GET /Crops/All
    @Test
    void getAllCrops() throws Exception {
        List<CropDTO> crops = Arrays.asList(
                new CropDTO("60b63b8f3e111f8d44d45e72", Status.Perfect_plant.name(), Type.TOMATO.name(), "676ae2a9b909de5f9607fcb6"),
                new CropDTO("60b63b8f3e111f8d44d45e73", Status.Healthy_state.name(), Type.LETTUCE.name(), "676ae2a9b909de5f9607fcb7")
        );
        when(serviceCrop.getAllCrops()).thenReturn(crops);

        mockMvc.perform(get("/Crops/All"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("60b63b8f3e111f8d44d45e72"))
                .andExpect(jsonPath("$[1].id").value("60b63b8f3e111f8d44d45e73"))
                .andExpect(jsonPath("$[0].status").value("Perfect_plant"))
                .andExpect(jsonPath("$[1].status").value("Healthy_state"))
                .andExpect(jsonPath("$[0].type").value("TOMATO"))
                .andExpect(jsonPath("$[1].type").value("LETTUCE"));
    }

    // Test para GET /Crops/id/{id}
    @Test
    void getCropById() throws Exception {
        CropDTO crop = new CropDTO("60b63b8f3e111f8d44d45e72", Status.Perfect_plant.name(), Type.TOMATO.name(), "676ae2a9b909de5f9607fcb6");
        when(serviceCrop.getCropById("60b63b8f3e111f8d44d45e72")).thenReturn(crop);

        mockMvc.perform(get("/Crops/id/{id}", "60b63b8f3e111f8d44d45e72"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("60b63b8f3e111f8d44d45e72"))
                .andExpect(jsonPath("$.status").value("Perfect_plant"))
                .andExpect(jsonPath("$.type").value("TOMATO"))
                .andExpect(jsonPath("$.user").value("676ae2a9b909de5f9607fcb6"));
    }

    // Test para GET /Crops/status/{status}
    @Test
    void getCropsByStatus() throws Exception {
        List<CropDTO> crops = Arrays.asList(
                new CropDTO("60b63b8f3e111f8d44d45e72", Status.Perfect_plant.name(), Type.TOMATO.name(), "676ae2a9b909de5f9607fcb6"),
                new CropDTO("60b63b8f3e111f8d44d45e73", Status.Perfect_plant.name(), Type.LETTUCE.name(), "676ae2a9b909de5f9607fcb7")
        );
        when(serviceCrop.getCropsByStatus(Status.Perfect_plant.name())).thenReturn(crops);

        mockMvc.perform(get("/Crops/status/{status}", Status.Perfect_plant.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("Perfect_plant"))
                .andExpect(jsonPath("$[1].status").value("Perfect_plant"));
    }

    // Test para GET /Crops/type/{type}
    @Test
    void getCropsByType() throws Exception {
        List<CropDTO> crops = Arrays.asList(
                new CropDTO("60b63b8f3e111f8d44d45e72", Status.Perfect_plant.name(), Type.TOMATO.name(), "676ae2a9b909de5f9607fcb6"),
                new CropDTO("60b63b8f3e111f8d44d45e73", Status.Healthy_state.name(), Type.LETTUCE.name(), "676ae2a9b909de5f9607fcb7")
        );
        when(serviceCrop.getCropsByType(Type.TOMATO.name())).thenReturn(crops);

        mockMvc.perform(get("/Crops/type/{type}", Type.TOMATO.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("TOMATO"))
                .andExpect(jsonPath("$[1].type").value("LETTUCE"));
    }

    // Test para GET /Crops/user/{id}
    @Test
    void getCropByUser() throws Exception {
        List<CropDTO> crops = Arrays.asList(
                new CropDTO("60b63b8f3e111f8d44d45e72", Status.Perfect_plant.name(), Type.TOMATO.name(), "676ae2a9b909de5f9607fcb6")
        );
        when(serviceCrop.getCropsByUser("676ae2a9b909de5f9607fcb6")).thenReturn(crops);

        mockMvc.perform(get("/Crops/user/{id}", "676ae2a9b909de5f9607fcb6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user").value("676ae2a9b909de5f9607fcb6"));
    }

    // Test para GET /Crops/count/{id}
    @Test
    void countCropsByUser() throws Exception {
        when(serviceCrop.countCropsByUser("676ae2a9b909de5f9607fcb6")).thenReturn(3L);

        mockMvc.perform(get("/Crops/count/{id}", "676ae2a9b909de5f9607fcb6"))
                .andExpect(status().isOk())
                .andExpect(content().string("3"));
    }

    // Test para PUT /Crops/Update/{id}
    @Test
    void updateCrop() throws Exception {
        CropDTO updatedCrop = new CropDTO("60b63b8f3e111f8d44d45e72", Status.Healthy_state.name(), Type.LETTUCE.name(), "676ae2a9b909de5f9607fcb6");
        when(serviceCrop.updatedCrop(any(String.class), any(CropDTO.class))).thenReturn(updatedCrop);

        mockMvc.perform(put("/Crops/Update/{id}", "60b63b8f3e111f8d44d45e72")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedCrop)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("60b63b8f3e111f8d44d45e72"))
                .andExpect(jsonPath("$.status").value("Healthy_state"))
                .andExpect(jsonPath("$.type").value("LETTUCE"))
                .andExpect(jsonPath("$.user").value("676ae2a9b909de5f9607fcb6"));
    }

    // Test para DELETE /Crops/Delete/{id}
    @Test
    void deleteCrop() throws Exception {
        doNothing().when(serviceCrop).deleteCrop("60b63b8f3e111f8d44d45e72");

        mockMvc.perform(delete("/Crops/Delete/{id}", "60b63b8f3e111f8d44d45e72"))
                .andExpect(status().isNoContent());
    }
}