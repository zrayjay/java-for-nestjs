import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** Disposable data only: never attach this exercise to a real application with user data. */
public final class DiagnosticsWorkload {
    private static final Object MONITOR = new Object();
    private static final List<byte[]> RETAINED = new ArrayList<>();
    private static volatile byte[] scratch;
    private static volatile long checksum;
    private static void cpu(long deadline) {
        long value = 7;
        while (System.nanoTime() < deadline) {
            for (int index = 0; index < 50000; index++) { value = (value * 1664525 + 1013904223) ^ (value >>> 11); }
            checksum = value;
        }
    }
    public static void main(String[] args) throws Exception {
        for (int index = 0; index < 2048; index++) { RETAINED.add(new byte[8192]); }
        long deadline = System.nanoTime() + 12_000_000_000L;
        var held = new CountDownLatch(1);
        var holder = new Thread(() -> {
            synchronized (MONITOR) {
                held.countDown();
                while (System.nanoTime() < deadline) {
                    try { Thread.sleep(10); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); return; }
                }
            }
        }, "lab-holder");
        holder.start();
        held.await();
        var blocked = new Thread(() -> { synchronized (MONITOR) { checksum ^= 1; } }, "lab-blocked");
        blocked.start();
        var worker = new Thread(() -> cpu(deadline), "lab-cpu");
        worker.start();
        var allocator = new Thread(() -> {
            while (System.nanoTime() < deadline) {
                for (int index = 0; index < 16; index++) { scratch = new byte[4096]; }
                try { Thread.sleep(2); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); return; }
            }
        }, "lab-allocator");
        allocator.start();
        System.out.println("DIAGNOSTICS_READY=" + ProcessHandle.current().pid());
        System.out.flush();
        worker.join();
        allocator.join();
        holder.join();
        blocked.join();
        System.out.println("DIAGNOSTICS_DONE retained=" + RETAINED.size() + " checksum=" + checksum);
    }
}
