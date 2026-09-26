package app.smartpot.api.mqtt.service;

import app.smartpot.api.mqtt.config.MqttProperties;
import app.smartpot.api.mqtt.model.MqttConnectedEvent;
import app.smartpot.api.mqtt.model.MqttMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cliente Paho con reconexión automática. Los mensajes se procesan en hilos virtuales para no
 * bloquear el hilo de callbacks de Paho.
 */
@Slf4j
public class PahoMqttGateway implements MqttGateway, MqttCallbackExtended,
        ApplicationListener<ApplicationReadyEvent>, DisposableBean {

    private static final long PUBLISH_TIMEOUT_MS = 5_000;
    private static final long RETRY_DELAY_MS = 5_000;

    private final MqttAsyncClient client;
    private final MqttConnectOptions options;
    private final MqttTopicResolver topics;
    private final ApplicationEventPublisher publisher;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean running = true;

    public PahoMqttGateway(MqttProperties properties, MqttTopicResolver topics, ApplicationEventPublisher publisher)
            throws MqttException {
        this.topics = topics;
        this.publisher = publisher;
        String clientId = properties.clientId() + "-" + UUID.randomUUID().toString().substring(0, 8);
        this.client = new MqttAsyncClient(properties.brokerUri(), clientId, new MemoryPersistence());
        this.client.setCallback(this);
        this.options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setMaxReconnectDelay(30_000);
        options.setKeepAliveInterval(30);
        options.setConnectionTimeout(10);
        options.setMaxInflight(100);
        if (properties.username() != null && !properties.username().isBlank()) {
            options.setUserName(properties.username());
        }
        if (properties.password() != null && !properties.password().isBlank()) {
            options.setPassword(properties.password().toCharArray());
        }
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        executor.submit(this::connectUntilReady);
    }

    private void connectUntilReady() {
        while (running && !client.isConnected()) {
            try {
                client.connect(options).waitForCompletion(15_000);
                return;
            } catch (MqttException ex) {
                log.warn("No se pudo conectar al broker MQTT ({}); se reintenta en {} s",
                        ex.getMessage(), RETRY_DELAY_MS / 1000);
                sleep();
            }
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverUri) {
        try {
            List<String> subscriptions = new ArrayList<>(topics.subscriptions());
            subscriptions.add(MqttTopicResolver.CONTROL_RESPONSE_TOPIC);
            int[] qos = subscriptions.stream().mapToInt(topic -> 1).toArray();
            client.subscribe(subscriptions.toArray(String[]::new), qos);
            log.info("Conectado al broker MQTT {} ({})", serverUri, reconnect ? "reconexión" : "primera conexión");
            publisher.publishEvent(new MqttConnectedEvent(reconnect));
        } catch (MqttException ex) {
            log.error("No se pudo suscribir a los tópicos de SmartPot", ex);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("Se perdió la conexión con el broker MQTT: {}", cause == null ? "sin detalle" : cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        executor.submit(() -> publisher.publishEvent(new MqttMessageEvent(topic, payload)));
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Sin acción: la confirmación de negocio llega por el tópico de ACK.
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isConnected() {
        return client.isConnected();
    }

    @Override
    public boolean publish(String topic, String payload, int qos, boolean retained) {
        if (!client.isConnected()) {
            return false;
        }
        try {
            client.publish(topic, payload.getBytes(StandardCharsets.UTF_8), qos, retained)
                    .waitForCompletion(PUBLISH_TIMEOUT_MS);
            return true;
        } catch (MqttException ex) {
            log.warn("No se pudo publicar en {}: {}", topic, ex.getMessage());
            return false;
        }
    }

    @Override
    public void destroy() {
        running = false;
        try {
            if (client.isConnected()) {
                client.disconnect().waitForCompletion(3_000);
            }
            client.close();
        } catch (MqttException ex) {
            log.debug("Cierre del cliente MQTT con error", ex);
        }
        executor.shutdownNow();
    }

    private void sleep() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
