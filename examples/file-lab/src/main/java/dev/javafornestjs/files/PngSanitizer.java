package dev.javafornestjs.files;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.InflaterInputStream;
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
        validateChunks(input, width, height);
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
    private static void validateChunks(byte[] input, int width, int height) throws IOException {
        if (input[24] != 8 || (input[25] != 2 && input[25] != 6) || input[26] != 0 || input[27] != 0 || input[28] != 0) {
            throw new IllegalArgumentException("Only noninterlaced 8-bit RGB/RGBA PNG is supported");
        }
        var bytes = ByteBuffer.wrap(input);
        var compressed = new ByteArrayOutputStream();
        int offset = 8;
        int headers = 0;
        boolean ended = false;
        boolean sawData = false;
        boolean dataEnded = false;
        while (offset < input.length) {
            if (input.length - offset < 12) { throw new IllegalArgumentException("Invalid PNG chunk structure"); }
            int length = bytes.getInt(offset);
            if (length < 0 || length > input.length - offset - 12) { throw new IllegalArgumentException("Invalid PNG chunk length"); }
            String type = new String(input, offset + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int end = offset + 12 + length;
            var crc = new CRC32();
            crc.update(input, offset + 4, length + 4);
            if (crc.getValue() != Integer.toUnsignedLong(bytes.getInt(end - 4))) { throw new IllegalArgumentException("Invalid PNG CRC"); }
            switch (type) {
                case "IHDR" -> {
                    headers++;
                    if (headers != 1 || offset != 8 || length != 13) { throw new IllegalArgumentException("Duplicate or misplaced PNG header"); }
                }
                case "IDAT" -> {
                    if (dataEnded) { throw new IllegalArgumentException("Noncontiguous PNG image data"); }
                    sawData = true;
                    compressed.write(input, offset + 8, length);
                }
                case "IEND" -> {
                    if (!sawData || length != 0 || end != input.length) { throw new IllegalArgumentException("Invalid PNG ending"); }
                    ended = true;
                }
                case "tEXt" -> { if (sawData) { dataEnded = true; } }
                default -> throw new IllegalArgumentException("Unsupported PNG chunk");
            }
            offset = end;
        }
        if (headers != 1 || !ended) { throw new IllegalArgumentException("Incomplete PNG structure"); }
        int expected = height * (1 + width * (input[25] == 6 ? 4 : 3));
        try (var inflater = new InflaterInputStream(new ByteArrayInputStream(compressed.toByteArray()))) {
            byte[] pixels = inflater.readNBytes(expected + 1);
            if (pixels.length != expected) { throw new IllegalArgumentException("Invalid PNG scanline length"); }
        } catch (IOException error) { throw new IllegalArgumentException("Invalid PNG compression", error); }
    }
}
