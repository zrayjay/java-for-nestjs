package dev.javafornestjs.messaging;

import java.time.Duration;

/** One serialized teaching relay instance; leases fence SQL marking, never broker publishing. */
public final class OutboxRelay {
    @FunctionalInterface public interface Publisher { void send(TaskEvent event) throws Exception; }
    public enum Result { EMPTY, SENT, LEASE_LOST, CIRCUIT_OPEN }
    private final MessageStore store;
    private final Publisher publisher;
    private final int leaseMillis;
    private final long cooldownNanos;
    private int failures;
    private long openedAt;
    public OutboxRelay(MessageStore store, Publisher publisher, int leaseMillis, Duration cooldown) {
        if (leaseMillis < 100 || leaseMillis > 5000 || cooldown.isNegative() || cooldown.isZero() || cooldown.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException("Explicit bounded teaching lease and cooldown required");
        }
        this.store = store;
        this.publisher = publisher;
        this.leaseMillis = leaseMillis;
        cooldownNanos = cooldown.toNanos();
    }
    public synchronized Result runOnce(Runnable afterConfirm) throws Exception {
        if (failures >= 2 && System.nanoTime() - openedAt < cooldownNanos) { return Result.CIRCUIT_OPEN; }
        var claim = store.claim(leaseMillis);
        if (claim.isEmpty()) { return Result.EMPTY; }
        try { publisher.send(claim.get().event()); }
        catch (Exception error) {
            failures++;
            if (failures >= 2) { openedAt = System.nanoTime(); }
            // Keep pending; do not mistake a failed/unknown send for delivery.
            throw error;
        }
        failures = 0;
        afterConfirm.run();
        var result = store.markSent(claim.get()) ? Result.SENT : Result.LEASE_LOST;
        System.out.println("OUTBOX_RESULT " + result + " eventId=" + claim.get().event().eventId());
        return result;
    }
}
