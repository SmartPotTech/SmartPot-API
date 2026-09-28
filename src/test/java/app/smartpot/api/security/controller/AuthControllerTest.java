package app.smartpot.api.security.controller;

import app.smartpot.api.cache.CacheStore;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.security.model.dto.AuthResponse;
import app.smartpot.api.security.service.AuthService;
import app.smartpot.api.support.WebTestConfig;
import app.smartpot.api.users.model.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@Import(WebTestConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private CacheStore cacheStore;

    @Test
    void registerIsPublicAndReturnsTheToken() throws Exception {
        UserResponse user = new UserResponse("u1", "Ana", "Gómez", "ana@example.com", "USER", Instant.now());
        when(authService.register(any())).thenReturn(new AuthResponse("jwt-token", Instant.now(), user));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"lastName\":\"Gómez\",\"email\":\"ana@example.com\",\"password\":\"Tomate2026\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.user.email").value("ana@example.com"));
    }

    @Test
    void validationErrorsAreReportedInSpanishPerField() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"lastName\":\"\",\"email\":\"no-es-correo\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Revisa los datos enviados"))
                .andExpect(jsonPath("$.fields.email").value("El correo no es válido"))
                .andExpect(jsonPath("$.fields.lastName").exists());
    }

    @Test
    void wrongCredentialsReturn401WithAGenericMessage() throws Exception {
        when(authService.login(any())).thenThrow(ApiException.unauthorized("Correo o contraseña incorrectos"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.com\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Correo o contraseña incorrectos"));
    }

    @Test
    void forgotPasswordAlwaysAnswers202() throws Exception {
        mvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nadie@example.com\"}"))
                .andExpect(status().isAccepted());

        verify(authService).requestPasswordReset(anyString());
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{roto"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El cuerpo de la solicitud no es un JSON válido"));
    }

    @Test
    void tooManyRequestsAreThrottled() throws Exception {
        when(cacheStore.increment(anyString(), any())).thenReturn(1_000_000L);

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.com\",\"password\":\"x\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
