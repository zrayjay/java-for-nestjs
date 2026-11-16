package dev.javafornestjs.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DeadlineContractTest {
    @Test void localWaitDeadlineDoesNotCancelTheAlreadyStartedOperation() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var effects = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var operation = executor.submit(() -> {
                started.countDown();
                if (!release.await(2, TimeUnit.SECONDS)) { throw new IllegalStateException("Fixture gate not released"); }
                return effects.incrementAndGet();
            });
            try {
                assertTrue(started.await(1, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> operation.get(30, TimeUnit.MILLISECONDS));
                assertFalse(operation.isCancelled());
                assertEquals(0, effects.get());
            } finally { release.countDown(); }
            assertEquals(1, operation.get(1, TimeUnit.SECONDS));
            assertEquals(1, effects.get());
        }
    }
}
