package app.smartpot.api.commands.service;

import app.smartpot.api.actuators.model.dto.ActuatorDTO;
import app.smartpot.api.actuators.model.entity.ActuatorType;
import app.smartpot.api.actuators.service.ActuatorService;
import app.smartpot.api.commands.mapper.CommandMapper;
import app.smartpot.api.commands.model.dto.CommandDTO;
import app.smartpot.api.commands.model.entity.Command;
import app.smartpot.api.commands.model.entity.CommandStatus;
import app.smartpot.api.commands.repository.CommandRepository;
import app.smartpot.api.crops.model.dto.CropDTO;
import app.smartpot.api.crops.service.CropService;
import app.smartpot.api.mqtt.service.MqttCommandPublisher;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandServiceImplTest {
    @Test
    void createCommandPublishesMqttCommandAfterPersisting() throws Exception {
        CommandRepository commandRepository = mock(CommandRepository.class);
        CropService cropService = mock(CropService.class);
        CommandMapper commandMapper = mock(CommandMapper.class);
        ActuatorService actuatorService = mock(ActuatorService.class);
        MqttCommandPublisher mqttCommandPublisher = mock(MqttCommandPublisher.class);

        CommandServiceImpl service = new CommandServiceImpl(
                commandRepository,
                cropService,
                commandMapper,
                actuatorService,
                mqttCommandPublisher
        );

        String cropId = "507f1f77bcf86cd799439012";
        String actuatorId = "507f1f77bcf86cd799439013";
        String commandId = "507f1f77bcf86cd799439011";

        CommandDTO input = new CommandDTO();
        input.setCommandType("ACTIVATE_WATER_PUMP");
        input.setCrop(cropId);
        input.setActuator(actuatorId);

        CropDTO crop = new CropDTO();
        crop.setId(cropId);

        Command entity = new Command();
        entity.setId(new ObjectId(commandId));
        entity.setCommandType("ACTIVATE_WATER_PUMP");
        entity.setCrop(new ObjectId(cropId));
        entity.setActuator(new ObjectId(actuatorId));
        entity.setStatus(CommandStatus.PENDING);

        CommandDTO saved = new CommandDTO();
        saved.setId(commandId);
        saved.setCommandType("ACTIVATE_WATER_PUMP");
        saved.setCrop(cropId);
        saved.setActuator(actuatorId);
        saved.setStatus(CommandStatus.PENDING);

        ActuatorDTO actuator = new ActuatorDTO();
        actuator.setId(actuatorId);
        actuator.setCrop(cropId);
        actuator.setType(ActuatorType.WATER_PUMP);

        when(cropService.getCropById(cropId)).thenReturn(crop);
        when(commandMapper.toEntity(input)).thenReturn(entity);
        when(commandRepository.save(entity)).thenReturn(entity);
        when(commandMapper.toDTO(entity)).thenReturn(saved);
        when(actuatorService.getActuatorById(actuatorId)).thenReturn(actuator);

        CommandDTO result = service.createCommand(input);

        assertEquals(commandId, result.getId());
        verify(commandRepository).save(entity);
        verify(mqttCommandPublisher).publish(saved, ActuatorType.WATER_PUMP);
    }
}
