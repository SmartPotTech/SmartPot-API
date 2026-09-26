package app.smartpot.api.cache;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheStoreTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC);
    private final CacheStore store = new CacheStore(redis, clock);

    CacheStoreTest() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("sin conexión"));
    }

    @Test
    void countsLocallyWhenRedisIsDown() {
        assertThat(store.increment("rate:1", Duration.ofMinutes(1))).isEqualTo(1);
        assertThat(store.increment("rate:1", Duration.ofMinutes(1))).isEqualTo(2);
        assertThat(store.increment("rate:2", Duration.ofMinutes(1))).isEqualTo(1);
    }

    @Test
    void setIfAbsentOnlySucceedsOncePerWindow() {
        assertThat(store.setIfAbsent("cooldown", Duration.ofMinutes(5))).isTrue();
        assertThat(store.setIfAbsent("cooldown", Duration.ofMinutes(5))).isFalse();
    }

    @Test
    void keepsValuesUntilTheyExpire() {
        store.put("profiles", "[]", Duration.ofHours(1));

        assertThat(store.get("profiles")).contains("[]");
        assertThat(store.get("missing")).isEmpty();
    }
}
