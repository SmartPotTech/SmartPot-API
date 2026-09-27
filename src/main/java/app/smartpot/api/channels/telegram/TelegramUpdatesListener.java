package app.smartpot.api.channels.telegram;

import app.smartpot.api.channels.config.TelegramProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;

/**
 * Recibe los mensajes del bot. En modo «webhook» registra la URL pública y Telegram los envía a
 * {@link TelegramWebhookController}; en modo «polling» (local y demo, sin dirección pública) los consulta con
 * sondeo largo en un hilo virtual. Con varias réplicas de la API se usa webhook.
 */
@Slf4j
@Component
public class TelegramUpdatesListener implements SmartLifecycle {

    private static final Duration MAX_BACKOFF = Duration.ofMinutes(1);

    private final TelegramProperties properties;
    private final TelegramClient client;
    private final TelegramBot bot;
    private volatile boolean running;
    private Thread poller;

    public TelegramUpdatesListener(TelegramProperties properties, TelegramClient client, TelegramBot bot) {
        this.properties = properties;
        this.client = client;
        this.bot = bot;
    }

    @Override
    public void start() {
        running = true;
        if (!properties.enabled()) {
            return;
        }
        try {
            client.setCommands();
            if (properties.webhook()) {
                client.setWebhook(properties.webhookUrl(), properties.webhookSecret());
                log.info("Telegram: webhook registrado en {}", properties.webhookUrl());
                return;
            }
            client.deleteWebhook();
        } catch (RestClientException ex) {
            log.warn("Telegram no respondió al configurar el bot: {}", ex.getMessage());
            if (properties.webhook()) {
                return;
            }
        }
        poller = Thread.ofVirtual().name("telegram-polling").start(this::poll);
        log.info("Telegram: recibiendo mensajes por sondeo largo");
    }

    private void poll() {
        long offset = 0;
        Duration backoff = Duration.ofSeconds(2);
        while (running) {
            try {
                List<TelegramUpdate> updates = client.getUpdates(offset);
                for (TelegramUpdate update : updates) {
                    offset = Math.max(offset, update.updateId() + 1);
                    handleSafely(update);
                }
                backoff = Duration.ofSeconds(2);
            } catch (RestClientException ex) {
                log.debug("Sondeo de Telegram falló: {}", ex.getMessage());
                if (!sleep(backoff)) {
                    return;
                }
                backoff = backoff.multipliedBy(2).compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff.multipliedBy(2);
            }
        }
    }

    void handleSafely(TelegramUpdate update) {
        try {
            bot.handle(update);
        } catch (RuntimeException ex) {
            log.warn("No se pudo atender un mensaje de Telegram: {}", ex.getMessage());
        }
    }

    private boolean sleep(Duration duration) {
        try {
            Thread.sleep(duration);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void stop() {
        running = false;
        if (poller != null) {
            poller.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
