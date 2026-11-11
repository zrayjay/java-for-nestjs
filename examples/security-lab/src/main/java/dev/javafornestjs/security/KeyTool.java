package dev.javafornestjs.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPairGenerator;
import java.util.Base64;

public final class KeyTool {
    private KeyTool() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 0) { throw new IllegalArgumentException("This teaching tool accepts no arguments"); }
        Path directory = Path.of("target", "demo-keys").toAbsolutePath().normalize();
        Path privatePath = directory.resolve("private.pem");
        Path publicPath = directory.resolve("public.pem");
        if (Files.exists(privatePath) || Files.exists(publicPath)) { throw new IllegalStateException("Refusing to overwrite existing keys"); }
        boolean created = Files.notExists(directory);
        Files.createDirectories(directory);
        boolean posix = Files.getFileStore(directory).supportsFileAttributeView("posix");
        if (created && posix) { Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------")); }
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        String privatePem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        String publicPem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder().encodeToString(pair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        Files.writeString(privatePath, privatePem, StandardOpenOption.CREATE_NEW);
        if (posix) { Files.setPosixFilePermissions(privatePath, PosixFilePermissions.fromString("rw-------")); }
        Files.writeString(publicPath, publicPem, StandardOpenOption.CREATE_NEW);
        System.out.println("Private key path: " + privatePath);
        System.out.println("Public key path: " + publicPath);
    }
}
