package app.smartpot.api.channels.telegram;

import app.smartpot.api.channels.config.TelegramProperties;
import app.smartpot.api.channels.service.ChannelDeliveryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente mínimo del Bot API de Telegram (https://core.telegram.org/bots/api).
 */
@Slf4j
@Component
public class TelegramClient {

    /**
     * Espera del sondeo largo: Telegram responde apenas llega un mensaje o al cumplirse este tiempo.
     */
    static final int POLL_SECONDS = 25;

    private final RestClient client;
    private final RestClient pollingClient;

    public TelegramClient(TelegramProperties properties, RestClient.Builder builder) {
        String base = properties.apiBaseUrl() + "/bot" + (properties.botToken() == null ? "" : properties.botToken());
        this.client = builder.clone().baseUrl(base).requestFactory(factory(Duration.ofSeconds(10))).build();
        this.pollingClient = builder.clone().baseUrl(base)
                .requestFactory(factory(Duration.ofSeconds(POLL_SECONDS + 15))).build();
    }

    private static JdkClientHttpRequestFactory factory(Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    private static void call(RestClient restClient, String method, Map<String, ?> body) {
        restClient.post()
                .uri("/" + method)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Envía un mensaje con formato HTML; con url agrega el botón para abrir SmartPot.
     */
    public void sendMessage(String chatId, String html, String buttonText, String url) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", html);
        body.put("parse_mode", "HTML");
        body.put("link_preview_options", Map.of("is_disabled", true));
        // Telegram solo acepta botones con direcciones públicas en https.
        if (url != null && url.startsWith("https://")) {
            body.put("reply_markup", Map.of("inline_keyboard", List.of(List.of(Map.of("text", buttonText, "url", url)))));
        }
        try {
            call(client, "sendMessage", body);
        } catch (HttpClientErrorException ex) {
            boolean permanent = ex.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN)
                    || ex.getResponseBodyAsString().contains("chat not found");
            throw new ChannelDeliveryException("Telegram rechazó el mensaje (" + ex.getStatusCode().value() + ")",
                    permanent);
        } catch (RestClientException ex) {
            throw new ChannelDeliveryException("Telegram no respondió", false);
        }
    }

    public List<TelegramUpdate> getUpdates(long offset) {
        TelegramUpdate.Updates updates = pollingClient.post()
                .uri("/getUpdates")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("offset", offset, "timeout", POLL_SECONDS, "allowed_updates", List.of("message")))
                .retrieve()
                .body(TelegramUpdate.Updates.class);
        return updates == null || updates.result() == null ? List.of() : updates.result();
    }

    public void setWebhook(String url, String secret) {
        call(client, "setWebhook", Map.of("url", url, "secret_token", secret, "allowed_updates", List.of("message")));
    }

    public void deleteWebhook() {
        call(client, "deleteWebhook", Map.of("drop_pending_updates", false));
    }

    /**
     * Menú de comandos que muestra la app de Telegram.
     */
    public void setCommands() {
        call(client, "setMyCommands", Map.of("commands", List.of(
                Map.of("command", "estado", "description", "Estado de tus cultivos"),
                Map.of("command", "desvincular", "description", "Dejar de recibir alertas aquí"),
                Map.of("command", "ayuda", "description", "Qué puedo hacer"))));
    }
}
