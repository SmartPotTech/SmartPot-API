package app.smartpot.api.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Contadores, llaves de enfriamiento y caché corta sobre Redis. Si Redis no responde,
 * usa memoria local durante un rato para que la API siga atendiendo.
 */
@Slf4j
@Component
public class CacheStore {

    private static final Duration REDIS_BACKOFF = Duration.ofSeconds(30);
    private static final String PREFIX = "smartpot:";

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final Map<String, LocalEntry> local = new ConcurrentHashMap<>();
    private volatile Instant redisRetryAt = Instant.EPOCH;

    public CacheStore(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    public long increment(String key, Duration ttl) {
        String fullKey = PREFIX + key;
        return withRedis(() -> {
            Long value = redis.opsForValue().increment(fullKey);
            if (value != null && value == 1L) {
                redis.expire(fullKey, ttl);
            }
            return value == null ? 1L : value;
        }, () -> local.compute(fullKey, (k, entry) -> entry == null || entry.isExpired(now())
                ? new LocalEntry("1", now().plus(ttl))
                : new LocalEntry(String.valueOf(Long.parseLong(entry.value()) + 1), entry.expiresAt())).asLong());
    }

    public boolean setIfAbsent(String key, Duration ttl) {
        String fullKey = PREFIX + key;
        return withRedis(() -> Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(fullKey, "1", ttl)), () -> {
            LocalEntry fresh = new LocalEntry("1", now().plus(ttl));
            LocalEntry current = local.merge(fullKey, fresh, (old, candidate) -> old.isExpired(now()) ? candidate : old);
            return current == fresh;
        });
    }

    public Optional<String> get(String key) {
        String fullKey = PREFIX + key;
        return withRedis(() -> Optional.ofNullable(redis.opsForValue().get(fullKey)), () -> {
            LocalEntry entry = local.get(fullKey);
            return entry == null || entry.isExpired(now()) ? Optional.empty() : Optional.of(entry.value());
        });
    }

    public void put(String key, String value, Duration ttl) {
        String fullKey = PREFIX + key;
        withRedis(() -> {
            redis.opsForValue().set(fullKey, value, ttl);
            return true;
        }, () -> {
            local.put(fullKey, new LocalEntry(value, now().plus(ttl)));
            return true;
        });
    }

    public boolean isRedisAvailable() {
        try {
            return "PONG".equalsIgnoreCase(redis.execute(connection -> connection.ping(), true));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Scheduled(fixedDelay = 60_000)
    void purgeExpired() {
        Instant now = now();
        local.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private <T> T withRedis(Supplier<T> redisCall, Supplier<T> fallback) {
        if (now().isBefore(redisRetryAt)) {
            return fallback.get();
        }
        try {
            return redisCall.get();
        } catch (RuntimeException ex) {
            redisRetryAt = now().plus(REDIS_BACKOFF);
            log.warn("Redis no está disponible; se usa memoria local durante {} s: {}", REDIS_BACKOFF.toSeconds(), ex.getMessage());
            return fallback.get();
        }
    }

    private Instant now() {
        return clock.instant();
    }

    private record LocalEntry(String value, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }

        long asLong() {
            return Long.parseLong(value);
        }
    }
}
