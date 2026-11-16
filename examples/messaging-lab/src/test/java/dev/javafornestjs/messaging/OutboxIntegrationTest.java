package dev.javafornestjs.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutboxIntegrationTest {
    private MessageStore store;
    private BrokerLab broker;
    @BeforeEach void fixture() throws Exception {
        store = new MessageStore(System.getenv("MESSAGE_JDBC_URL"), System.getenv("MESSAGE_DATABASE_USER"), System.getenv("MESSAGE_DATABASE_PASSWORD"));
        store.resetFixture();
        broker = new BrokerLab(System.getenv("MESSAGE_AMQP_URL"));
    }
    @AfterEach void close() throws Exception {
        try { if (broker != null) { broker.close(); } }
        finally { if (store != null) { store.close(); } }
    }
    private TaskEvent event() { return new TaskEvent(UUID.randomUUID().toString(), "task.created", "task-one"); }
    private BrokerLab.Outcome consume() throws Exception {
        try (var channel = broker.consumer()) { return broker.consume(channel, store, () -> { }, false).outcome(); }
    }
    @Test void businessAndOutboxRollbackTogether() {
        assertThrows(IllegalStateException.class, () -> store.enqueueTask(event(), () -> { throw new IllegalStateException("Injected before commit"); }));
        assertEquals(0, store.businessTasks());
        assertEquals(0, store.pending());
    }
    @Test void committedBusinessSurvivesActualUnroutablePublishThenRecovers() throws Exception {
        var event = event();
        store.enqueueTask(event, () -> { });
        var failing = new OutboxRelay(store, message -> {
            var result = broker.probeUnroutable(message);
            assertTrue(result.confirmed());
            assertTrue(result.returned());
            throw new IOException("Actual mandatory return: no route");
        }, 100, Duration.ofMillis(100));
        assertThrows(IOException.class, () -> failing.runOnce(() -> { }));
        assertEquals(1, store.businessTasks());
        assertEquals(1, store.pending());
        Thread.sleep(150);
        var healthy = new OutboxRelay(store, broker::send, 5000, Duration.ofMillis(100));
        assertEquals(OutboxRelay.Result.SENT, healthy.runOnce(() -> { }));
        assertEquals(BrokerLab.Outcome.APPLIED, consume());
        assertEquals(1, store.effects(event.taskKey()));
        assertEquals(0, store.pending());
    }
    @Test void confirmBeforeMarkCrashDuplicatesDeliveryButNotEffect() throws Exception {
        var event = event();
        store.enqueueTask(event, () -> { });
        var first = new OutboxRelay(store, broker::send, 100, Duration.ofMillis(100));
        assertThrows(IllegalStateException.class, () -> first.runOnce(() -> { throw new IllegalStateException("Injected after real confirm before mark"); }));
        assertEquals(BrokerLab.Outcome.APPLIED, consume());
        assertEquals(1, store.pending());
        Thread.sleep(150);
        var recovered = new OutboxRelay(store, broker::send, 5000, Duration.ofMillis(100));
        assertEquals(OutboxRelay.Result.SENT, recovered.runOnce(() -> { }));
        assertEquals(BrokerLab.Outcome.DUPLICATE, consume());
        assertEquals(1, store.effects(event.taskKey()));
        assertEquals(1, store.markers());
        assertEquals(0, store.pending());
    }
    @Test void concurrentLeaseAndStaleWorkerCannotMarkButCanStillPublish() throws Exception {
        var event = event();
        store.enqueueTask(event, () -> { });
        var first = store.claim(100).orElseThrow();
        assertTrue(store.claim(100).isEmpty());
        Thread.sleep(150);
        var current = store.claim(5000).orElseThrow();
        broker.send(first.event()); // Lease does not cancel or fence an external publisher.
        assertFalse(store.markSent(first));
        broker.send(current.event());
        assertTrue(store.markSent(current));
        assertEquals(BrokerLab.Outcome.APPLIED, consume());
        assertEquals(BrokerLab.Outcome.DUPLICATE, consume());
        assertEquals(1, store.effects(event.taskKey()));
        store.resetFixture();
        store.enqueueTask(event(), () -> { });
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = pool.submit(() -> store.claim(5000));
            var b = pool.submit(() -> store.claim(5000));
            assertEquals(1, (a.get(5, TimeUnit.SECONDS).isPresent() ? 1 : 0) + (b.get(5, TimeUnit.SECONDS).isPresent() ? 1 : 0));
        }
    }
    @Test void localCircuitStopsRepeatedActualRoutingFailuresAndAllowsRecoveryProbe() throws Exception {
        var event = event();
        store.enqueueTask(event, () -> { });
        var fail = new java.util.concurrent.atomic.AtomicBoolean(true);
        var relay = new OutboxRelay(store, message -> {
            if (fail.get()) {
                assertTrue(broker.probeUnroutable(message).returned());
                throw new IOException("Actual mandatory return");
            }
            broker.send(message);
        }, 100, Duration.ofMillis(200));
        assertThrows(IOException.class, () -> relay.runOnce(() -> { }));
        Thread.sleep(150);
        assertThrows(IOException.class, () -> relay.runOnce(() -> { }));
        assertEquals(OutboxRelay.Result.CIRCUIT_OPEN, relay.runOnce(() -> { }));
        assertEquals(1, store.pending());
        Thread.sleep(250);
        fail.set(false);
        assertEquals(OutboxRelay.Result.SENT, relay.runOnce(() -> { }));
        assertEquals(BrokerLab.Outcome.APPLIED, consume());
        assertEquals(0, store.pending());
    }
}
