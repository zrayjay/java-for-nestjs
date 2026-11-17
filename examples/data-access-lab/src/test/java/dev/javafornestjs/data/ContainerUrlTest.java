package dev.javafornestjs.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ContainerUrlTest {
    @Test void containerRuntimeIsExplicitAndCannotRetargetDestructiveFixtures() {
        String url = "jdbc:mysql://mysql:3306/data_lab?allowPublicKeyRetrieval=true&useSSL=false";
        assertThrows(IllegalArgumentException.class, () -> DatabaseUrl.dedicated(url));
        assertThrows(IllegalArgumentException.class, () -> DatabaseUrl.runtime(url, null));
        assertThrows(IllegalArgumentException.class, () -> DatabaseUrl.runtime(url, "production"));
        assertThrows(IllegalArgumentException.class, () -> DatabaseUrl.runtime(url.replace("data_lab", "production"), "compose"));
        assertThrows(IllegalArgumentException.class, () -> DatabaseUrl.runtime(url.replace("mysql:", "remote:"), "compose"));
        assertEquals(url, DatabaseUrl.runtime(url, "compose"));
        assertEquals("jdbc:mysql://127.0.0.1:3306/data_lab", DatabaseUrl.runtime("jdbc:mysql://127.0.0.1:3306/data_lab", null));
    }
}
