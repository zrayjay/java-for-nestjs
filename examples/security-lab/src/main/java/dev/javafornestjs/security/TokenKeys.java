package dev.javafornestjs.security;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

public final class TokenKeys {
    private TokenKeys() { }
    private static byte[] pem(String path, String type) throws Exception {
        if (path == null || path.isBlank()) { throw new IllegalArgumentException("Explicit JWT PEM paths are required"); }
        String value = Files.readString(Path.of(path)).replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(value);
    }
    public static KeyPair load(SecurityProperties properties, Environment environment) throws Exception {
        if (properties.ephemeralKeys()) {
            if (!environment.acceptsProfiles(Profiles.of("security-test"))) {
                throw new IllegalArgumentException("Ephemeral keys require the explicit security-test profile");
            }
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        }
        var factory = KeyFactory.getInstance("RSA");
        var privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(pem(properties.privateKeyPath(), "PRIVATE KEY")));
        var publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(pem(properties.publicKeyPath(), "PUBLIC KEY")));
        if (publicKey.getModulus().bitLength() < 2048) { throw new IllegalArgumentException("RSA2048 or stronger is required"); }
        var signature = Signature.getInstance("SHA256withRSA");
        byte[] challenge = "teaching-key-pair-check".getBytes(StandardCharsets.US_ASCII);
        signature.initSign(privateKey);
        signature.update(challenge);
        byte[] signed = signature.sign();
        signature.initVerify(publicKey);
        signature.update(challenge);
        if (!signature.verify(signed)) { throw new IllegalArgumentException("JWT key pair does not match"); }
        return new KeyPair(publicKey, privateKey);
    }
}
