package dev.javafornestjs.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DatabaseUrlTest {
    @Test
    void acceptsOnlyDedicatedLoopbackDatabaseAndReviewedParameters() {
        for (String value : Arrays.asList("jdbc:mysql://127.0.0.1:3306/data_lab",
                "jdbc:mysql://localhost:13307/data_lab?allowPublicKeyRetrieval=true&useSSL=false")) {
            assertEquals(value, DatabaseUrl.dedicated(value));
        }
    }
    @Test
    void rejectsOverridesOtherDatabasesRemoteHostsAndInvalidPortsBeforeConnecting() {
        for (String value : Arrays.asList(null, "jdbc:mysql://127.0.0.1:3306/production",
                "jdbc:mysql://remote:3306/data_lab", "jdbc:mysql://127.0.0.1:0/data_lab",
                "jdbc:mysql://127.0.0.1:65536/data_lab", "jdbc:mysql://127.0.0.1:3306/data_lab?dbname=production",
                "jdbc:mysql://127.0.0.1:3306/data_lab?%64bname=production",
                "jdbc:mysql://127.0.0.1:3306/data_lab?allowPublicKeyRetrieval=true&useSSL=false&dbname=production")) {
            assertThrows(IllegalArgumentException.class, () -> DatabaseUrl.dedicated(value));
        }
    }
}
