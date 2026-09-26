package app.smartpot.api.mqtt.service;

import app.smartpot.api.support.TestProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MqttTopicResolverTest {

    private static final String CROP = "6718f0a1b2c3d4e5f6a7b8c9";

    private final MqttTopicResolver resolver = new MqttTopicResolver(TestProperties.mqtt());

    @Test
    void buildsTheV1Topics() {
        assertThat(resolver.telemetry(CROP)).isEqualTo("smartpot/v1/" + CROP + "/telemetry");
        assertThat(resolver.commands(CROP)).isEqualTo("smartpot/v1/" + CROP + "/commands");
        assertThat(resolver.commandAck(CROP)).isEqualTo("smartpot/v1/" + CROP + "/commands/ack");
        assertThat(resolver.status(CROP)).isEqualTo("smartpot/v1/" + CROP + "/status");
    }

    @Test
    void parsesEachKindOfIncomingTopic() {
        assertThat(resolver.parse(resolver.telemetry(CROP)))
                .contains(new MqttTopicResolver.ParsedTopic(CROP, MqttTopicResolver.Kind.TELEMETRY));
        assertThat(resolver.parse(resolver.commandAck(CROP)))
                .contains(new MqttTopicResolver.ParsedTopic(CROP, MqttTopicResolver.Kind.COMMAND_ACK));
        assertThat(resolver.parse(resolver.status(CROP)))
                .contains(new MqttTopicResolver.ParsedTopic(CROP, MqttTopicResolver.Kind.STATUS));
    }

    @Test
    void ignoresForeignOrMalformedTopics() {
        assertThat(resolver.parse("smartpot/v1/not-an-id/telemetry")).isEmpty();
        assertThat(resolver.parse("smartpot/v2/" + CROP + "/telemetry")).isEmpty();
        assertThat(resolver.parse(resolver.commands(CROP))).isEmpty();
        assertThat(resolver.parse(null)).isEmpty();
    }

    @Test
    void deviceAclPatternsUseTheUsernamePlaceholder() {
        assertThat(resolver.devicePublishPatterns()).allMatch(topic -> topic.contains("/%u/"));
        assertThat(resolver.deviceSubscribePattern()).isEqualTo("smartpot/v1/%u/commands");
        assertThat(resolver.subscriptions()).contains("smartpot/v1/+/telemetry", "smartpot/v1/+/commands/ack");
    }
}
