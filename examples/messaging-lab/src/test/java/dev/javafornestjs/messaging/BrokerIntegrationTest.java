package dev.javafornestjs.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BrokerIntegrationTest {
    private MessageStore store;
    private BrokerLab broker;
    @BeforeEach
    void fixture() throws Exception {
        store = new MessageStore(System.getenv("MESSAGE_JDBC_URL"), System.getenv("MESSAGE_DATABASE_USER"), System.getenv("MESSAGE_DATABASE_PASSWORD"));
        store.resetFixture();
        broker = new BrokerLab(System.getenv("MESSAGE_AMQP_URL"));
    }
    @AfterEach
    void close() throws Exception {
        try { if (broker != null) { broker.close(); } }
        finally { if (store != null) { store.close(); } }
    }
    private TaskEvent event() { return new TaskEvent(UUID.randomUUID().toString(), "task.created", "task-one"); }
    private BrokerLab.DeliveryResult once(Runnable work) throws Exception {
        try (var channel = broker.consumer()) { return broker.consume(channel, store, work, false); }
    }
    private void assertDeadReason() throws Exception {
        var dead = broker.readDead();
        Object deaths = dead.getProps().getHeaders().get("x-death");
        assertTrue(deaths instanceof List<?>);
        Object first = ((List<?>) deaths).getFirst();
        assertTrue(first instanceof Map<?, ?>);
        assertEquals("rejected", ((Map<?, ?>) first).get("reason").toString());
    }
    @Test
    void confirmsAndMandatoryReturnsDescribeDifferentBoundaries() throws Exception {
        var event = event();
        var missing = broker.probeUnroutable(event);
        assertTrue(missing.confirmed());
        assertTrue(missing.returned());
        broker.send(event);
        assertEquals(BrokerLab.Outcome.APPLIED, once(() -> { }).outcome());
        assertEquals(1, store.effects(event.taskKey()));
        assertEquals(1, store.markers());
    }
    @Test
    void commitBeforeAckCanRedeliverWithoutRepeatingSqlEffect() throws Exception {
        var event = event();
        broker.send(event);
        Channel first = broker.consumer();
        try {
            var result = broker.consume(first, store, () -> { }, true);
            assertEquals(BrokerLab.Outcome.COMMITTED_UNACKED, result.outcome());
            assertFalse(first.isOpen());
        } finally { if (first.isOpen()) { first.close(); } }
        assertEquals(1, store.effects(event.taskKey()));
        var duplicate = once(() -> { throw new AssertionError("Duplicate must not execute business effect"); });
        assertTrue(duplicate.redelivered());
        assertEquals(BrokerLab.Outcome.DUPLICATE, duplicate.outcome());
        assertEquals(1, store.effects(event.taskKey()));
        assertEquals(1, store.markers());
    }
    @Test
    void boundedRetriesRollbackBeforeNativeDeadLettering() throws Exception {
        var event = event();
        broker.send(event);
        for (int attempt = 0; attempt < 3; attempt++) {
            var result = once(() -> { throw new IllegalStateException("Injected before SQL commit"); });
            assertEquals(attempt, result.attempt());
            assertEquals(attempt < 2 ? BrokerLab.Outcome.RETRY : BrokerLab.Outcome.DEAD, result.outcome());
            assertEquals(0, store.effects(event.taskKey()));
            assertEquals(0, store.markers());
        }
        assertDeadReason();
    }
    @Test
    void transientFailureCanRecoverAndPermanentPayloadsNeverTouchSql() throws Exception {
        var event = event();
        broker.send(event);
        assertEquals(BrokerLab.Outcome.RETRY, once(() -> { throw new IllegalStateException("One failure"); }).outcome());
        var recovered = once(() -> { });
        assertEquals(BrokerLab.Outcome.APPLIED, recovered.outcome());
        assertEquals(1, recovered.attempt());
        assertEquals(1, store.effects(event.taskKey()));
        for (byte[] bytes : List.of("{".getBytes(StandardCharsets.UTF_8), new byte[1025])) {
            broker.sendRaw(bytes, 0);
            assertEquals(BrokerLab.Outcome.DEAD, once(() -> { }).outcome());
            assertDeadReason();
        }
        for (Object attempt : List.of(-1, 3, "1", 0.5)) {
            broker.sendRaw(event().encode(), attempt);
            assertEquals(BrokerLab.Outcome.DEAD, once(() -> { }).outcome());
            assertDeadReason();
        }
        broker.send(new TaskEvent(event.eventId(), "task.created", "other-task"));
        assertEquals(BrokerLab.Outcome.DEAD, once(() -> { }).outcome());
        assertDeadReason();
        assertEquals(1, store.effects(event.taskKey()));
        assertEquals(0, store.effects("other-task"));
        assertEquals(1, store.markers());
    }
    @Test
    void concurrentDuplicatesHaveOneTransactionalSqlEffect() throws Exception {
        var event = event();
        broker.send(event);
        broker.send(event);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(() -> {
                start.await();
                return once(() -> { });
            });
            var second = pool.submit(() -> {
                start.await();
                return once(() -> { });
            });
            start.countDown();
            var outcomes = List.of(first.get(8, TimeUnit.SECONDS).outcome(), second.get(8, TimeUnit.SECONDS).outcome());
            assertTrue(outcomes.contains(BrokerLab.Outcome.APPLIED));
            assertTrue(outcomes.contains(BrokerLab.Outcome.DUPLICATE));
        }
        assertEquals(1, store.effects(event.taskKey()));
        assertEquals(1, store.markers());
    }
}
