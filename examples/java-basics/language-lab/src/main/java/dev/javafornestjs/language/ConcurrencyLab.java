package dev.javafornestjs.language;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ConcurrencyLab {
    private ConcurrencyLab() { }

    public record VirtualResult(List<Integer> values, boolean bothVirtual) { }
    private record TaskResult(int value, boolean virtual) { }

    public static VirtualResult virtualTasks() throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("release timeout");
                }
                return new TaskResult(10, Thread.currentThread().isVirtual());
            });
            var second = executor.submit(() -> {
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("release timeout");
                }
                return new TaskResult(20, Thread.currentThread().isVirtual());
            });
            try {
                if (!entered.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("tasks did not enter concurrently");
                }
            } finally {
                release.countDown();
            }
            var firstResult = first.get(5, TimeUnit.SECONDS);
            var secondResult = second.get(5, TimeUnit.SECONDS);
            return new VirtualResult(List.of(firstResult.value(), secondResult.value()),
                    firstResult.virtual() && secondResult.virtual());
        }
    }

    public static boolean timeoutDoesNotCancel() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> {
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("release timeout");
                }
                return 42;
            });
            try {
                if (!entered.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("task did not enter");
                }
                try {
                    future.get(0, TimeUnit.NANOSECONDS);
                    throw new IllegalStateException("blocked task cannot have completed");
                } catch (TimeoutException expected) {
                    return !future.isCancelled() && !future.isDone();
                }
            } finally {
                release.countDown();
                future.get(5, TimeUnit.SECONDS);
            }
        }
    }

    public static Throwable failureCause() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> { throw new IllegalArgumentException("bad-input"); });
            try {
                future.get(5, TimeUnit.SECONDS);
                throw new IllegalStateException("expected failure");
            } catch (ExecutionException failure) {
                return failure.getCause();
            }
        }
    }
}
