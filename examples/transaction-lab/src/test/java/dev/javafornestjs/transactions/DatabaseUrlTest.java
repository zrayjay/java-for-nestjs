package dev.javafornestjs.transactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DatabaseUrlTest {
    @Test
    void acceptsOnlyTheDocumentedDedicatedDatabaseUrls() {
        for (String url : Arrays.asList(
                "jdbc:mysql://127.0.0.1:13307/transaction_lab",
                "jdbc:mysql://localhost:3306/transaction_lab?allowPublicKeyRetrieval=true&useSSL=false")) {
            assertEquals(url, TransactionLab.dedicatedDatabaseUrl(url));
        }
    }

    @Test
    void rejectsDatabaseOverridesAndUnreviewedConnectionPropertiesBeforeConnecting() {
        for (String url : Arrays.asList(null,
                "jdbc:mysql://remote:3306/transaction_lab",
                "jdbc:mysql://127.0.0.1:3306/production",
                "jdbc:mysql://127.0.0.1:3306/transaction_lab?dbname=production",
                "jdbc:mysql://127.0.0.1:3306/transaction_lab?DBNAME=production",
                "jdbc:mysql://127.0.0.1:3306/transaction_lab?%64bname=production",
                "jdbc:mysql://127.0.0.1:3306/transaction_lab?allowPublicKeyRetrieval=true&useSSL=false&dbname=production",
                "jdbc:mysql://127.0.0.1:3306/transaction_lab?allowLoadLocalInfile=true")) {
            assertThrows(IllegalArgumentException.class, () -> TransactionLab.dedicatedDatabaseUrl(url));
        }
    }
}
