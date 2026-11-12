package dev.javafornestjs.cache;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** A bounded-key teaching cache. Callers provide current authorization and scoped database reads. */
public final class ScopedCache {
    private static final DefaultRedisScript<Long> FILL = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1]) or '0'
            if current ~= ARGV[1] then return 0 end
            redis.call('PSETEX', KEYS[2], ARGV[2], ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> INVALIDATE = new DefaultRedisScript<>("""
            redis.call('INCR', KEYS[1])
            redis.call('DEL', KEYS[2])
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;
    private final String namespace;
    private final long positiveMillis;
    private final long negativeMillis;
    private final LongUnaryOperator jitter;
    private final ConcurrentHashMap<String, CompletableFuture<Optional<String>>> flights = new ConcurrentHashMap<>();

    public ScopedCache(StringRedisTemplate redis, String namespace, Duration positive, Duration negative,
                       LongUnaryOperator jitter) {
        if (!namespace.matches("[a-zA-Z0-9_-]{1,60}") || positive.toMillis() < 1 || negative.toMillis() < 1) {
            throw new IllegalArgumentException("Invalid cache configuration");
        }
        this.redis = redis;
        this.namespace = namespace;
        this.positiveMillis = positive.toMillis();
        this.negativeMillis = negative.toMillis();
        this.jitter = jitter;
    }
    private String base(String scope, int id) {
        if (!scope.matches("[a-zA-Z0-9_-]{1,60}") || id < 1) { throw new IllegalArgumentException("Invalid cache key"); }
        return namespace + ":{" + scope + ":" + id + "}";
    }
    public Optional<String> read(String scope, int id, Runnable authorize, Supplier<Optional<String>> scopedLoad) {
        authorize.run(); // Every caller, including hits and local waiters, must pass current authorization.
        String base = base(scope, id);
        String generation = Optional.ofNullable(redis.opsForValue().get(base + ":epoch")).orElse("0");
        String stored = redis.opsForValue().get(base + ":value");
        if (stored != null) { return decode(stored); }
        String flightKey = base + ":" + generation;
        var mine = new CompletableFuture<Optional<String>>();
        var existing = flights.putIfAbsent(flightKey, mine);
        if (existing != null) { return existing.join(); }
        try {
            String rechecked = redis.opsForValue().get(base + ":value");
            if (rechecked != null) {
                Optional<String> value = decode(rechecked);
                mine.complete(value);
                return value;
            }
            Optional<String> value = scopedLoad.get();
            long ttl = value.isPresent() ? positiveMillis : negativeMillis;
            long extra = jitter.applyAsLong(ttl);
            if (extra < 0 || extra > ttl / 5) { throw new IllegalArgumentException("Jitter must be 0..20 percent"); }
            redis.execute(FILL, List.of(base + ":epoch", base + ":value"), generation,
                    Long.toString(Math.addExact(ttl, extra)), value.map(text -> "V" + text).orElse("N"));
            mine.complete(value);
            return value;
        } catch (RuntimeException | Error error) {
            mine.completeExceptionally(error);
            throw error;
        } finally {
            flights.remove(flightKey, mine);
        }
    }
    private Optional<String> decode(String value) {
        if (value.equals("N")) { return Optional.empty(); }
        if (value.startsWith("V")) { return Optional.of(value.substring(1)); }
        throw new IllegalStateException("Invalid cache encoding");
    }
    /** Invoke only AFTER a successful database commit, for every affected scope. */
    public void invalidateAfterCommit(String scope, int id) {
        String base = base(scope, id);
        redis.execute(INVALIDATE, List.of(base + ":epoch", base + ":value"));
    }
}
