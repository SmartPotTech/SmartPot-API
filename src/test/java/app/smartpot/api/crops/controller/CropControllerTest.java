package app.smartpot.api.crops.controller;

import app.smartpot.api.cache.CacheStore;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.model.entity.CropForm;
import app.smartpot.api.crops.model.entity.CropKind;
import app.smartpot.api.crops.model.entity.CropType;
import app.smartpot.api.crops.service.CropService;
import app.smartpot.api.crops.service.CropWeatherService;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.readings.service.ReadingService;
import app.smartpot.api.support.WebTestConfig;
import app.smartpot.api.virtualdevices.service.VirtualDeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CropController.class)
@Import(WebTestConfig.class)
class CropControllerTest {

    private static final String OWNER = "6718f0a1b2c3d4e5f6a7b000";
    private static final String CROP = "6718f0a1b2c3d4e5f6a7b8c9";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CropService cropService;

    @MockitoBean
    private ReadingService readingService;

    @MockitoBean
    private CacheStore cacheStore;

    @MockitoBean
    private VirtualDeviceService virtualDeviceService;

    @MockitoBean
    private CropWeatherService weatherService;

    @Test
    void requiresAToken() throws Exception {
        mvc.perform(get("/api/v1/crops"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Inicia sesión para continuar"));
    }

    @Test
    void rejectsInvalidTokens() throws Exception {
        mvc.perform(get("/api/v1/crops").header("Authorization", "Bearer no-es-un-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listsOnlyTheCallersCrops() throws Exception {
        Crop crop = Crop.builder().id(CROP).ownerId(OWNER).name("Lechugas").type(CropType.LETTUCE)
                .createdAt(Instant.now()).build();
        when(cropService.list(OWNER)).thenReturn(List.of(crop));
        when(readingService.latest(CROP)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/crops").with(jwt().jwt(token -> token.subject(OWNER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(CROP))
                .andExpect(jsonPath("$[0].type").value("LETTUCE"))
                .andExpect(jsonPath("$[0].device.online").value(false));
    }

    @Test
    void foreignCropsLookMissing() throws Exception {
        when(cropService.getOwned(anyString(), anyString())).thenThrow(ApiException.notFound("El cultivo no existe"));

        mvc.perform(get("/api/v1/crops/" + CROP).with(jwt().jwt(token -> token.subject("intruso"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("El cultivo no existe"));
    }

    @Test
    void validatesTheCropType() throws Exception {
        mvc.perform(post("/api/v1/crops").with(jwt().jwt(token -> token.subject(OWNER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Menta\",\"type\":\"MINT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void automationRequiresAnExplicitValue() throws Exception {
        mvc.perform(put("/api/v1/crops/" + CROP + "/automation").with(jwt().jwt(token -> token.subject(OWNER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.enabled").exists());
    }

    @Test
    void bulkAutomationAppliesToTheSelectedCrops() throws Exception {
        Crop crop = Crop.builder().id(CROP).ownerId(OWNER).name("Lechugas").type(CropType.LETTUCE)
                .automationEnabled(true).createdAt(Instant.now()).build();
        when(cropService.setAutomation(OWNER, List.of(CROP), true)).thenReturn(List.of(crop));
        when(readingService.latest(CROP)).thenReturn(Optional.empty());

        mvc.perform(put("/api/v1/crops/automation").with(jwt().jwt(token -> token.subject(OWNER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cropIds\":[\"" + CROP + "\"],\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].automationEnabled").value(true));
    }

    @Test
    void createsAVirtualCropAndStartsItsSimulationWithoutCredentials() throws Exception {
        Crop crop = Crop.builder().id(CROP).ownerId(OWNER).name("Fresas").type(CropType.STRAWBERRY)
                .kind(CropKind.VIRTUAL).form(CropForm.NFT).createdAt(Instant.now()).build();
        when(cropService.create(eq(OWNER), any())).thenReturn(new CropService.CreatedCrop(crop, null));

        mvc.perform(post("/api/v1/crops").with(jwt().jwt(token -> token.subject(OWNER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fresas\",\"type\":\"STRAWBERRY\",\"kind\":\"VIRTUAL\",\"form\":\"NFT\","
                                + "\"virtual\":{\"mode\":\"AUTO\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.crop.kind").value("VIRTUAL"))
                .andExpect(jsonPath("$.crop.form").value("NFT"))
                .andExpect(jsonPath("$.device").doesNotExist());
        verify(virtualDeviceService).checkCanCreate(eq(OWNER), any(), any());
        verify(virtualDeviceService).startFor(eq(crop), any());
    }

    @Test
    void realCropsRejectASimulationSetup() throws Exception {
        mvc.perform(post("/api/v1/crops").with(jwt().jwt(token -> token.subject(OWNER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tomates\",\"type\":\"TOMATO\",\"kind\":\"REAL\","
                                + "\"virtual\":{\"mode\":\"AUTO\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Solo los cultivos virtuales tienen simulación"));
        verify(cropService, never()).create(anyString(), any());
    }

    @Test
    void cropsReportTheirKindAndForm() throws Exception {
        Crop legacy = Crop.builder().id(CROP).ownerId(OWNER).name("Lechugas").type(CropType.LETTUCE)
                .createdAt(Instant.now()).build();
        when(cropService.getOwned(OWNER, CROP)).thenReturn(legacy);
        when(readingService.latest(CROP)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/crops/" + CROP).with(jwt().jwt(token -> token.subject(OWNER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("REAL"))
                .andExpect(jsonPath("$.form").value("POT"));
    }
}
