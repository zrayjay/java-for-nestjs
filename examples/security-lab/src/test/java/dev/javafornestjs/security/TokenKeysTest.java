package dev.javafornestjs.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.StandardEnvironment;

class TokenKeysTest {
    private SecurityProperties properties(boolean ephemeral, String privatePath, String publicPath) {
        return new SecurityProperties("https://java-for-nestjs.example", "teaching-api", 300, 604800,
                ephemeral, privatePath, publicPath);
    }
    private KeyPair pair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
    private Path pem(Path path, String type, byte[] value) throws Exception {
        Files.writeString(path, "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder().encodeToString(value)
                + "\n-----END " + type + "-----\n");
        return path;
    }
    @Test
    void missingExplicitKeysAndImplicitEphemeralFallbackAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> TokenKeys.load(properties(false, null, null), new StandardEnvironment()));
        assertThrows(IllegalArgumentException.class, () -> TokenKeys.load(properties(true, null, null), new StandardEnvironment()));
    }
    @Test
    void ephemeralKeysRequireExplicitTestProfile() throws Exception {
        var environment = new StandardEnvironment();
        environment.setActiveProfiles("security-test");
        assertEquals("RSA", TokenKeys.load(properties(true, null, null), environment).getPublic().getAlgorithm());
    }
    @Test
    void explicitPkcs8AndX509KeysMustActuallyMatch(@TempDir Path directory) throws Exception {
        var expected = pair();
        var other = pair();
        Path privatePath = pem(directory.resolve("private.pem"), "PRIVATE KEY", expected.getPrivate().getEncoded());
        Path publicPath = pem(directory.resolve("public.pem"), "PUBLIC KEY", expected.getPublic().getEncoded());
        var configured = properties(false, privatePath.toString(), publicPath.toString());
        assertEquals(expected.getPublic(), TokenKeys.load(configured, new StandardEnvironment()).getPublic());
        pem(publicPath, "PUBLIC KEY", other.getPublic().getEncoded());
        assertThrows(IllegalArgumentException.class, () -> TokenKeys.load(configured, new StandardEnvironment()));
    }
}
