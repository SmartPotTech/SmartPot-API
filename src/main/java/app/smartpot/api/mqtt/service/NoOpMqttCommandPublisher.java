package app.smartpot.api.mqtt.service;

import app.smartpot.api.actuators.model.entity.ActuatorType;
import app.smartpot.api.commands.model.dto.CommandDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnMissingBean(MqttCommandPublisher.class)
public class NoOpMqttCommandPublisher implements MqttCommandPublisher {
    @Override
    public void publish(CommandDTO command, ActuatorType actuatorType) {
        log.debug("MQTT disabled; command {} was not published", command.getId());
    }
}
