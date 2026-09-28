package app.smartpot.api.channels.telegram;

import app.smartpot.api.cache.CacheStore;
import app.smartpot.api.channels.config.TelegramProperties;
import app.smartpot.api.support.WebTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TelegramWebhookController.class)
@Import({WebTestConfig.class, TelegramWebhookControllerTest.Config.class})
class TelegramWebhookControllerTest {

    private static final String SECRET = "secreto-del-webhook-de-pruebas";
    private static final String UPDATE = """
            {"update_id": 7, "message": {"message_id": 1, "text": "/estado",
             "chat": {"id": 555, "type": "private"}, "from": {"id": 555, "first_name": "Ana"}}}""";
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private TelegramUpdatesListener listener;
    @MockitoBean
    private CacheStore cacheStore;

    @Test
    void acceptsUpdatesSignedWithTheSecretWithoutASession() throws Exception {
        mvc.perform(post("/api/v1/channels/telegram/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Bot-Api-Secret-Token", SECRET).content(UPDATE))
                .andExpect(status().isOk());
        verify(listener).handleSafely(any());
    }

    @Test
    void hidesTheEndpointWithoutTheSecret() throws Exception {
        mvc.perform(post("/api/v1/channels/telegram/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Bot-Api-Secret-Token", "otro").content(UPDATE))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/channels/telegram/webhook").contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                .andExpect(status().isNotFound());
        verify(listener, never()).handleSafely(any());
    }

    @TestConfiguration
    static class Config {
        @Bean
        TelegramProperties telegramProperties() {
            return new TelegramProperties("123:token", "SmartPotBot", "webhook",
                    "https://api.smartpot.test/api/v1/channels/telegram/webhook", SECRET, null);
        }
    }
}
