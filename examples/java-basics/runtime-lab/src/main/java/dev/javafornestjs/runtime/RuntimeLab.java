package dev.javafornestjs.runtime;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class RuntimeLab {
    private static volatile byte[] allocationSink;

    private RuntimeLab() { }

    public static int mix(int value) {
        return value * 31 + 7;
    }

    public static int checksum(int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) {
            sum += mix(i);
        }
        return sum;
    }

    /** Both workers read zero before either writes: a controlled lost update. */
    public static RaceResult sharedCounter() throws Exception {
        int[] unsafe = {0};
        var safe = new AtomicInteger();
        var bothRead = new CountDownLatch(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> increment(unsafe, safe, bothRead));
            var second = workers.submit(() -> increment(unsafe, safe, bothRead));
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }
        return new RaceResult(unsafe[0], safe.get());
    }

    private static void increment(int[] unsafe, AtomicInteger safe, CountDownLatch bothRead) {
        int snapshot = unsafe[0];
        bothRead.countDown();
        try {
            if (!bothRead.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Both workers must reach the read barrier");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
        unsafe[0] = snapshot + 1;
        safe.incrementAndGet();
    }

    public record RaceResult(int unsafe, int atomic) { }

    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "compute" : args[0];
        switch (mode) {
            case "compute" -> System.out.println("CHECKSUM=" + checksum(100_000));
            case "threads" -> {
                var result = sharedCounter();
                System.out.println("COUNTERS=" + result.unsafe() + "," + result.atomic());
            }
            case "allocate" -> {
                for (int i = 0; i < 256; i++) {
                    allocationSink = new byte[256 * 1024];
                }
                System.out.println("ALLOCATION_DONE=" + allocationSink.length);
            }
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }
}
