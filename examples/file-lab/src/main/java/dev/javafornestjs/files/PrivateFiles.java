package dev.javafornestjs.files;

import io.minio.GetBucketPolicyArgs;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Internal boundary: trustedActor must come from authenticated server context, never request JSON. */
public final class PrivateFiles {
    public enum ScanStatus { CLEAN, INFECTED, NOT_CONFIGURED }
    @FunctionalInterface
    public interface Scanner { ScanStatus scan(byte[] canonicalPng) throws Exception; }
    public record Receipt(String key, String scanStatus) { }
    public record Audit(int actor, String action, String key) { }
    private final MinioClient client;
    private final String bucket;
    private final Scanner scanner;
    private final Consumer<Audit> audit;
    public PrivateFiles(MinioClient client, String bucket, Scanner scanner, Consumer<Audit> audit) throws Exception {
        this.client = client;
        this.bucket = bucket;
        this.scanner = scanner;
        this.audit = audit;
        // Conservative teaching prerequisite: refuse any bucket policy, including public-read grants.
        try {
            String policy = client.getBucketPolicy(GetBucketPolicyArgs.builder().bucket(bucket).build());
            if (policy != null && !policy.isBlank()) { throw new IllegalArgumentException("Bucket policy must be absent"); }
        } catch (ErrorResponseException error) {
            if (!"NoSuchBucketPolicy".equals(error.errorResponse().code())) { throw error; }
        }
    }
    public Receipt upload(int trustedActor, String filename, String type, InputStream source) throws Exception {
        requireActor(trustedActor);
        byte[] canonical = PngSanitizer.sanitize(filename, type, source);
        ScanStatus status = scanner.scan(canonical);
        if (status == null || status == ScanStatus.INFECTED) { throw new SecurityException("Upload rejected"); }
        String key = UUID.randomUUID() + ".png";
        client.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                .stream(new ByteArrayInputStream(canonical), canonical.length, -1).contentType("image/png")
                .userMetadata(Map.of("owner-id", Integer.toString(trustedActor), "scan-status", status.name().toLowerCase(java.util.Locale.ROOT)))
                .build());
        try { audit.accept(new Audit(trustedActor, "file.created", key)); }
        catch (RuntimeException error) {
            try { client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build()); }
            catch (Exception cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
        return new Receipt(key, status.name().toLowerCase(java.util.Locale.ROOT));
    }
    public byte[] download(int trustedActor, String key) throws Exception {
        requireActor(trustedActor);
        if (key == null || !key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png")) {
            throw new IllegalArgumentException("Invalid object key");
        }
        var stat = client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
        if (!Integer.toString(trustedActor).equals(stat.userMetadata().get("owner-id"))) { throw new SecurityException("Forbidden"); }
        try (var input = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            byte[] bytes = input.readNBytes(PngSanitizer.MAX_BYTES + 1);
            if (bytes.length > PngSanitizer.MAX_BYTES) { throw new IllegalStateException("Stored object exceeds budget"); }
            audit.accept(new Audit(trustedActor, "file.read", key));
            return bytes;
        }
    }
    private static void requireActor(int actor) {
        if (actor < 1) { throw new SecurityException("Authentication required"); }
    }
}
