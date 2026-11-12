package dev.javafornestjs.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisCacheTest {
    private static LettuceConnectionFactory connection;
    private static StringRedisTemplate redis;
    private static ScopedCache cache;
    private static final String NAMESPACE = "java-" + UUID.randomUUID();
    private static final Runnable ALLOW = () -> { };
    @BeforeAll
    static void connect() {
        String url = System.getenv("CACHE_REDIS_URL");
        if (url == null || !url.matches("redis://(127\\.0\\.0\\.1|localhost):[0-9]+/?")) {
            throw new IllegalArgumentException("CACHE_REDIS_URL must name a disposable loopback Redis");
        }
        var address = URI.create(url);
        connection = new LettuceConnectionFactory(new RedisStandaloneConfiguration(address.getHost(), address.getPort()));
        connection.afterPropertiesSet();
        redis = new StringRedisTemplate(connection);
        cache = new ScopedCache(redis, NAMESPACE, Duration.ofSeconds(5), Duration.ofSeconds(1), ttl -> ttl / 5);
        try (var probe = connection.getConnection()) { assertEquals("PONG", probe.ping()); }
    }
    @AfterAll
    static void close() {
        if (redis != null) { redis.delete(redis.keys(NAMESPACE + ":*")); }
        if (connection != null) { connection.destroy(); }
    }
    @Test
    void positiveAndNullHitsHaveBoundedTtlAndScopeIsolation() throws Exception {
        var loads = new AtomicInteger();
        java.util.function.Supplier<Optional<String>> load = () -> { loads.incrementAndGet();
                    return Optional.of("task"); };
        assertEquals(Optional.of("task"), cache.read("department1", 1, ALLOW, load));
        cache.read("department1", 1, ALLOW, load);
        assertEquals(1, loads.get());
        long ttl = redis.getExpire(NAMESPACE + ":{department1:1}:value", TimeUnit.MILLISECONDS);
        assertTrue(ttl > 0 && ttl <= 6000);
        cache.read("department2", 1, ALLOW, load);
        assertEquals(2, loads.get());
        var misses = new AtomicInteger();
        java.util.function.Supplier<Optional<String>> absent = () -> { misses.incrementAndGet();
                    return Optional.empty(); };
        assertTrue(cache.read("department1", 2, ALLOW, absent).isEmpty());
        assertTrue(cache.read("department1", 2, ALLOW, absent).isEmpty());
        assertEquals(1, misses.get());
        String key = NAMESPACE + ":{department1:2}:value";
        long negative = redis.getExpire(key, TimeUnit.MILLISECONDS);
        assertTrue(negative > 0 && negative <= 1200);
        redis.expire(key, Duration.ofMillis(1));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (Boolean.TRUE.equals(redis.hasKey(key))) {
            assertTrue(System.nanoTime() < deadline, "Redis expiration timed out");
            Thread.sleep(5);
        }
        cache.read("department1", 2, ALLOW, absent);
        assertEquals(2, misses.get());
    }
    @Test
    void hitNeverBypassesCurrentAuthorization() {
        cache.read("department1", 3, ALLOW, () -> Optional.of("secret"));
        var loads = new AtomicInteger();
        assertThrows(SecurityException.class, () -> cache.read("department1", 3,
                () -> { throw new SecurityException("forbidden"); },
                () -> { loads.incrementAndGet();
                    return Optional.of("leak"); }));
        assertEquals(0, loads.get());
    }
    @Test
    void concurrentMissesShareOneLoadAndErrorsCanRetry() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var loads = new AtomicInteger();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var requests = new ArrayList<java.util.concurrent.Future<Optional<String>>>();
            for (int index = 0; index < 20; index++) {
                requests.add(pool.submit(() -> cache.read("department1", 4, ALLOW, () -> {
                    loads.incrementAndGet();
                    entered.countDown();
                    await(release);
                    return Optional.of("shared");
                })));
            }
            try { assertTrue(entered.await(5, TimeUnit.SECONDS)); }
            finally { release.countDown(); }
            for (var request : requests) { assertEquals(Optional.of("shared"), request.get(5, TimeUnit.SECONDS)); }
            assertEquals(1, loads.get());
        }
        assertThrows(IllegalStateException.class, () -> cache.read("department1", 5, ALLOW,
                () -> { throw new IllegalStateException("database down"); }));
        assertEquals(Optional.of("recovered"), cache.read("department1", 5, ALLOW, () -> Optional.of("recovered")));
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Timed out"); } }
        catch (InterruptedException error) { Thread.currentThread().interrupt();
            throw new IllegalStateException(error); }
    }
    @Test
    void postCommitInvalidationPreventsOldFill() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var old = pool.submit(() -> cache.read("department1", 6, ALLOW,
                    () -> { entered.countDown();
                    await(release);
                    return Optional.of("old"); }));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                cache.invalidateAfterCommit("department1", 6);
                assertEquals(Optional.of("new"), cache.read("department1", 6, ALLOW, () -> Optional.of("new")));
            } finally { release.countDown(); }
            assertEquals(Optional.of("old"), old.get(5, TimeUnit.SECONDS));
            assertEquals(Optional.of("new"), cache.read("department1", 6, ALLOW, () -> Optional.of("wrong")));
        }
    }
    @Test
    void unavailableRedisIsSurfaced() {
        var stopped = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 1));
        stopped.afterPropertiesSet();
        stopped.destroy();
        var broken = new ScopedCache(new StringRedisTemplate(stopped), "unavailable", Duration.ofSeconds(1), Duration.ofMillis(100), ttl -> 0);
        var loads = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> broken.read("department1", 1, ALLOW,
                () -> { loads.incrementAndGet();
                    return Optional.of("x"); }));
        assertEquals(0, loads.get());
    }
}
