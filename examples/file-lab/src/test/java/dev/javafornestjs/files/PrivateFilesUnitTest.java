package dev.javafornestjs.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PrivateFilesUnitTest {
    private static final String KEY = "00000000-0000-0000-0000-000000000001.png";
    private MinioClient client;
    @BeforeEach
    void freshBoundary() { client = mock(MinioClient.class); }
    private PrivateFiles files(PrivateFiles.Scanner scanner, java.util.function.Consumer<PrivateFiles.Audit> audit) throws Exception {
        return new PrivateFiles(client, "unit-files", scanner, audit);
    }
    @Test
    void missingActorAndInfectedVerdictNeverReachStorage() throws Exception {
        var files = files(bytes -> PrivateFiles.ScanStatus.INFECTED, event -> { });
        assertThatThrownBy(() -> files.upload(0, "a.png", "image/png", new ByteArrayInputStream(PngSanitizerTest.png())))
                .isInstanceOf(SecurityException.class).hasMessage("Authentication required");
        assertThatThrownBy(() -> files.upload(1, "a.png", "image/png", new ByteArrayInputStream(PngSanitizerTest.png())))
                .isInstanceOf(SecurityException.class).hasMessage("Upload rejected");
        verify(client, never()).putObject(any(PutObjectArgs.class));
    }
    @Test
    void auditAndCleanupFailuresRetainBothCausesAndSameRandomKey() throws Exception {
        var auditFailure = new IllegalStateException("Audit down");
        var cleanupFailure = new IOException("Delete down");
        doThrow(cleanupFailure).when(client).removeObject(any(RemoveObjectArgs.class));
        var files = files(bytes -> PrivateFiles.ScanStatus.NOT_CONFIGURED, event -> { throw auditFailure; });
        assertThatThrownBy(() -> files.upload(1, "a.png", "image/png", new ByteArrayInputStream(PngSanitizerTest.png())))
                .isSameAs(auditFailure).hasSuppressedException(cleanupFailure);
        var put = ArgumentCaptor.forClass(PutObjectArgs.class);
        var remove = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(client).putObject(put.capture());
        verify(client).removeObject(remove.capture());
        assertThat(remove.getValue().object()).isEqualTo(put.getValue().object()).endsWith(".png");
    }
    @Test
    void foreignOwnerNeverReadsObjectBody() throws Exception {
        var stat = mock(StatObjectResponse.class);
        when(stat.userMetadata()).thenReturn(Map.of("owner-id", "2"));
        when(client.statObject(any(StatObjectArgs.class))).thenReturn(stat);
        var files = files(bytes -> PrivateFiles.ScanStatus.NOT_CONFIGURED, event -> { });
        assertThatThrownBy(() -> files.download(1, KEY)).isInstanceOf(SecurityException.class).hasMessage("Forbidden");
        verify(client, never()).getObject(any(GetObjectArgs.class));
    }
    @Test
    void downloadAuditFailureDoesNotReturnBytesAndClosesBody() throws Exception {
        var stat = mock(StatObjectResponse.class);
        when(stat.userMetadata()).thenReturn(Map.of("owner-id", "1"));
        when(client.statObject(any(StatObjectArgs.class))).thenReturn(stat);
        var body = mock(GetObjectResponse.class);
        when(body.readNBytes(PngSanitizer.MAX_BYTES + 1)).thenReturn(PngSanitizerTest.png());
        when(client.getObject(any(GetObjectArgs.class))).thenReturn(body);
        var files = files(bytes -> PrivateFiles.ScanStatus.NOT_CONFIGURED, event -> { throw new IllegalStateException("Audit down"); });
        assertThatThrownBy(() -> files.download(1, KEY)).isInstanceOf(IllegalStateException.class).hasMessage("Audit down");
        verify(body).close();
    }
}
