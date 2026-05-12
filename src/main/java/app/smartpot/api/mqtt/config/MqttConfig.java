package app.smartpot.api.mqtt.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import app.smartpot.api.mqtt.service.MqttTopicResolver;

@Configuration
@IntegrationComponentScan(basePackages = "app.smartpot.api.mqtt")
@ConditionalOnProperty(prefix = "application.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttConfig {

    @Bean
    public MqttConnectOptions mqttConnectOptions(MqttProperties properties) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{properties.getBrokerUri()});
        options.setCleanSession(properties.isCleanSession());
        options.setAutomaticReconnect(true);
        options.setKeepAliveInterval(properties.getKeepAliveSeconds());
        options.setConnectionTimeout(properties.getConnectionTimeoutSeconds());

        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            options.setUserName(properties.getUsername());
        }
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            options.setPassword(properties.getPassword().toCharArray());
        }

        return options;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory(MqttConnectOptions mqttConnectOptions) {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(mqttConnectOptions);
        return factory;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInboundAdapter(
            MqttProperties properties,
            MqttPahoClientFactory mqttClientFactory,
            MqttTopicResolver mqttTopicResolver,
            @Qualifier("mqttInputChannel") MessageChannel mqttInputChannel) {
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                properties.getClientId() + "-in",
                mqttClientFactory,
                mqttTopicResolver.sensorTopicPattern(),
                mqttTopicResolver.commandAckTopicPattern()
        );
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(properties.getTelemetryQos(), properties.getCommandQos());
        adapter.setOutputChannel(mqttInputChannel);
        return adapter;
    }

    @Bean
    public MessageHandler mqttOutboundHandler(MqttProperties properties, MqttPahoClientFactory mqttClientFactory) {
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(properties.getClientId() + "-out", mqttClientFactory);
        handler.setAsync(true);
        handler.setDefaultQos(properties.getCommandQos());
        handler.setDefaultRetained(false);
        return handler;
    }

    @Bean
    public IntegrationFlow mqttOutboundFlow(
            @Qualifier("mqttOutboundChannel") MessageChannel mqttOutboundChannel,
            MessageHandler mqttOutboundHandler) {
        return IntegrationFlow.from(mqttOutboundChannel)
                .handle(mqttOutboundHandler)
                .get();
    }
}
