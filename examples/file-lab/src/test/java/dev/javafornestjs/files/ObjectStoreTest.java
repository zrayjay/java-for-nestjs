package dev.javafornestjs.files;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ObjectStoreTest {
    private static MinioClient client;
    private static String endpoint;
    private static final String BUCKET = "java-files-" + UUID.randomUUID();
    @BeforeAll
    static void connect() throws Exception {
        endpoint = System.getenv("FILES_ENDPOINT");
        if (endpoint == null || !endpoint.matches("http://(127\\.0\\.0\\.1|localhost):[0-9]+")) {
            throw new IllegalArgumentException("FILES_ENDPOINT must name a disposable loopback object service");
        }
        client = MinioClient.builder().endpoint(endpoint).credentials(System.getenv("FILES_ACCESS_KEY"), System.getenv("FILES_SECRET_KEY")).build();
        client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
    }
    @AfterAll
    static void close() throws Exception {
        if (client == null) { return; }
        for (var item : client.listObjects(ListObjectsArgs.builder().bucket(BUCKET).build())) {
            client.removeObject(RemoveObjectArgs.builder().bucket(BUCKET).object(item.get().objectName()).build());
        }
        client.removeBucket(RemoveBucketArgs.builder().bucket(BUCKET).build());
    }
    private PrivateFiles files(java.util.function.Consumer<PrivateFiles.Audit> audit) throws Exception {
        return new PrivateFiles(client, BUCKET, bytes -> PrivateFiles.ScanStatus.NOT_CONFIGURED, audit);
    }
    @Test
    void privateStorageOwnerDownloadAndRedactedAuditAreReal() throws Exception {
        var events = new ArrayList<PrivateFiles.Audit>();
        var files = files(events::add);
        var receipt = files.upload(1, "private-name.png", "image/png", new ByteArrayInputStream(PngSanitizerTest.png()));
        assertEquals("not_configured", receipt.scanStatus());
        assertFalse(receipt.key().contains("private-name"));
        assertArrayEquals(PngSanitizer.sanitize("a.png", "image/png", new ByteArrayInputStream(PngSanitizerTest.png())), files.download(1, receipt.key()));
        assertThrows(SecurityException.class, () -> files.download(2, receipt.key()));
        assertThrows(SecurityException.class, () -> files.download(0, receipt.key()));
        assertThrows(IllegalArgumentException.class, () -> files.download(1, "../" + receipt.key()));
        try (var http = HttpClient.newHttpClient()) {
            var response = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/" + BUCKET + "/" + receipt.key())).GET().build(), HttpResponse.BodyHandlers.discarding());
            assertEquals(403, response.statusCode());
        }
        assertEquals(2, events.size());
        assertEquals(new PrivateFiles.Audit(1, "file.created", receipt.key()), events.getFirst());
        assertFalse(events.toString().contains("private-name"));
    }
    @Test
    void scannerRejectionAndScannerFailureDoNotStoreObjects() throws Exception {
        var infected = new PrivateFiles(client, BUCKET, bytes -> PrivateFiles.ScanStatus.INFECTED, event -> { });
        var broken = new PrivateFiles(client, BUCKET, bytes -> { throw new IllegalStateException("Scanner down"); }, event -> { });
        long before = count();
        assertThrows(SecurityException.class, () -> infected.upload(1, "a.png", "image/png", new ByteArrayInputStream(PngSanitizerTest.png())));
        assertThrows(IllegalStateException.class, () -> broken.upload(1, "a.png", "image/png", new ByteArrayInputStream(PngSanitizerTest.png())));
        assertEquals(before, count());
    }
    @Test
    void auditFailureCompensatesStorageAndPublicPolicyIsRejected() throws Exception {
        long before = count();
        var broken = files(event -> { throw new IllegalStateException("Audit down"); });
        assertThrows(IllegalStateException.class, () -> broken.upload(1, "a.png", "image/png", new ByteArrayInputStream(PngSanitizerTest.png())));
        assertEquals(before, count());
        String policyBucket = "policy-" + UUID.randomUUID();
        client.makeBucket(MakeBucketArgs.builder().bucket(policyBucket).build());
        try {
            String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::" + policyBucket + "/*\"]}]}";
            client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(policyBucket).config(policy).build());
            assertThrows(IllegalArgumentException.class, () -> new PrivateFiles(client, policyBucket, bytes -> PrivateFiles.ScanStatus.NOT_CONFIGURED, event -> { }));
        } finally { client.removeBucket(RemoveBucketArgs.builder().bucket(policyBucket).build()); }
    }
    private long count() throws Exception {
        long count = 0;
        for (var item : client.listObjects(ListObjectsArgs.builder().bucket(BUCKET).build())) {
            item.get();
            count++;
        }
        return count;
    }
}
