package dev.javafornestjs.files;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import javax.imageio.ImageIO;

public final class PngSanitizer {
    public static final int MAX_BYTES = 1024 * 1024;
    private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private PngSanitizer() { }
    public static byte[] sanitize(String filename, String contentType, InputStream source) throws IOException {
        if (filename == null || !filename.matches("[A-Za-z0-9][A-Za-z0-9._ -]{0,99}\\.png")
                || filename.contains("..") || !"image/png".equals(contentType)) {
            throw new IllegalArgumentException("Invalid PNG upload");
        }
        byte[] input = source.readNBytes(MAX_BYTES + 1);
        if (input.length > MAX_BYTES || input.length < 33 || !Arrays.equals(SIGNATURE, Arrays.copyOf(input, 8))) {
            throw new IllegalArgumentException("Invalid PNG upload");
        }
        var header = ByteBuffer.wrap(input);
        if (header.getInt(8) != 13 || header.getInt(12) != 0x49484452) { throw new IllegalArgumentException("Invalid PNG header"); }
        int width = header.getInt(16);
        int height = header.getInt(20);
        if (width < 1 || height < 1 || width > 1024 || height > 1024 || (long) width * height > 1048576) {
            throw new IllegalArgumentException("PNG pixel budget exceeded");
        }
        BufferedImage decoded;
        try { decoded = ImageIO.read(new ByteArrayInputStream(input)); }
        catch (IOException error) { throw new IllegalArgumentException("Invalid PNG pixels", error); }
        if (decoded == null || decoded.getWidth() != width || decoded.getHeight() != height) {
            throw new IllegalArgumentException("Invalid PNG pixels");
        }
        // Copy pixels into a fresh image: do not retain client ancillary metadata or appended bytes.
        var canonical = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        canonical.setRGB(0, 0, width, height, decoded.getRGB(0, 0, width, height, null, 0, width), 0, width);
        var output = new ByteArrayOutputStream();
        if (!ImageIO.write(canonical, "png", output) || output.size() > MAX_BYTES) {
            throw new IllegalArgumentException("Canonical PNG exceeds size budget");
        }
        return output.toByteArray();
    }
}
