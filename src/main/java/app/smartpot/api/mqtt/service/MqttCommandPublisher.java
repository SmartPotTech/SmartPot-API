package app.smartpot.api.mqtt.service;

import app.smartpot.api.actuators.model.entity.ActuatorType;
import app.smartpot.api.commands.model.dto.CommandDTO;

public interface MqttCommandPublisher {
    void publish(CommandDTO command, ActuatorType actuatorType);
}
