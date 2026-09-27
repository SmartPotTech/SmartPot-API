package app.smartpot.api.virtualdevices.service;

import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.virtualdevices.model.entity.VirtualDevice;
import app.smartpot.api.virtualdevices.repository.VirtualDeviceRepository;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CropKindBackfillTest {

    private static final String CROP = "6718f0a1b2c3d4e5f6a7b8c9";

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final VirtualDeviceRepository repository = mock(VirtualDeviceRepository.class);
    private final CropKindBackfill backfill = new CropKindBackfill(mongoTemplate, repository);

    @Test
    void cropsWithAVirtualPotBecomeVirtualAndTheRestReal() {
        when(repository.findAll()).thenReturn(List.of(VirtualDevice.builder().cropId(CROP).build()));
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(Crop.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        backfill.backfill();

        ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(3)).updateMulti(queries.capture(), updates.capture(), eq(Crop.class));
        assertThat(queries.getAllValues().getFirst().getQueryObject().toJson()).contains(CROP);
        assertThat(updates.getAllValues().get(0).getUpdateObject().toJson()).contains("VIRTUAL");
        assertThat(updates.getAllValues().get(1).getUpdateObject().toJson()).contains("REAL");
        assertThat(updates.getAllValues().get(2).getUpdateObject().toJson()).contains("POT");
    }

    @Test
    void withoutVirtualPotsEveryLegacyCropIsReal() {
        when(repository.findAll()).thenReturn(List.of());
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(Crop.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        backfill.backfill();

        verify(mongoTemplate, times(2)).updateMulti(any(Query.class), any(Update.class), eq(Crop.class));
    }
}
