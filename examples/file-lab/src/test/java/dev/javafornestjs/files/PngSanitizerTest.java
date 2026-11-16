package dev.javafornestjs.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PngSanitizerTest {
    static byte[] png() throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xffff0000);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
    @Test
    void validPngIsReencodedWithPreservedPixels() throws Exception {
        byte[] result = PngSanitizer.sanitize("avatar.png", "image/png", new ByteArrayInputStream(png()));
        var image = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(2, image.getWidth());
        assertEquals(0xffff0000, image.getRGB(0, 0));
        assertTrue(result.length < PngSanitizer.MAX_BYTES);
    }
    @Test
    void pathTraversalMimeSpoofAndWrongSignatureAreRejected() throws Exception {
        byte[] valid = png();
        for (String name : new String[]{"../secret.png", "..\\secret.png", "/tmp/a.png", "a.png.exe", "a\r\nb.png"}) {
            assertThrows(IllegalArgumentException.class, () -> PngSanitizer.sanitize(name, "image/png", new ByteArrayInputStream(valid)));
        }
        assertThrows(IllegalArgumentException.class, () -> PngSanitizer.sanitize("a.png", "text/html", new ByteArrayInputStream(valid)));
        assertThrows(IllegalArgumentException.class, () -> PngSanitizer.sanitize("a.png", "image/png", new ByteArrayInputStream("<script>".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }
    @Test
    void byteAndPixelBombsAreRejectedBeforeDecode() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> PngSanitizer.sanitize("a.png", "image/png", new ByteArrayInputStream(new byte[PngSanitizer.MAX_BYTES + 1])));
        byte[] bomb = png();
        ByteBuffer.wrap(bomb).putInt(16, Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> PngSanitizer.sanitize("a.png", "image/png", new ByteArrayInputStream(bomb)));
        assertThrows(IllegalArgumentException.class, () -> PngSanitizer.sanitize("a.png", "image/png", new ByteArrayInputStream(java.util.Arrays.copyOf(png(), 34))));
    }
    static byte[] chunk(String type, byte[] data) throws Exception {
        var output = new ByteArrayOutputStream();
        output.write(ByteBuffer.allocate(4).putInt(data.length).array());
        byte[] tag = type.getBytes(StandardCharsets.US_ASCII);
        output.write(tag);
        output.write(data);
        var crc = new CRC32();
        crc.update(tag);
        crc.update(data);
        output.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
        return output.toByteArray();
    }
    static byte[] join(byte[]... parts) throws Exception {
        var output = new ByteArrayOutputStream();
        for (byte[] part : parts) { output.write(part); }
        return output.toByteArray();
    }
    @Test
    void metadataIsRemovedAndDuplicateHeaderInterlaceAndExpansionAreRejected() throws Exception {
        byte[] valid = png();
        byte[] marker = "Comment\0UNTRUSTED_SCRIPT_MARKER".getBytes(StandardCharsets.US_ASCII);
        byte[] metadata = join(java.util.Arrays.copyOf(valid, 33), chunk("tEXt", marker), java.util.Arrays.copyOfRange(valid, 33, valid.length));
        byte[] sanitized = PngSanitizer.sanitize("a.png", "image/png", new ByteArrayInputStream(metadata));
        assertEquals(0xffff0000, ImageIO.read(new ByteArrayInputStream(sanitized)).getRGB(0, 0));
        assertTrue(!new String(sanitized, StandardCharsets.ISO_8859_1).contains("UNTRUSTED_SCRIPT_MARKER"));
        byte[] duplicate = join(java.util.Arrays.copyOf(valid, 33), java.util.Arrays.copyOfRange(valid, 8, valid.length));
        var repeated = assertThrows(IllegalArgumentException.class, () -> PngSanitizer.sanitize("a.png", "image/png", new ByteArrayInputStream(duplicate)));
        assertEquals("Duplicate or misplaced PNG header", repeated.getMessage());
        byte[] header = java.util.Arrays.copyOfRange(valid, 16, 29);
        header[12] = 1;
        byte[] interlaced = join(java.util.Arrays.copyOf(valid, 8), chunk("IHDR", header), java.util.Arrays.copyOfRange(valid, 33, valid.length));
        var interlace = assertThrows(IllegalArgumentException.class, () -> PngSanitizer.sanitize("a.png", "image/png", new ByteArrayInputStream(interlaced)));
        assertEquals("Only noninterlaced 8-bit RGB/RGBA PNG is supported", interlace.getMessage());
        header[12] = 0;
        var compressed = new ByteArrayOutputStream();
        try (var deflater = new DeflaterOutputStream(compressed)) { deflater.write(new byte[8 * 1024 * 1024]); }
        byte[] expansion = join(java.util.Arrays.copyOf(valid, 8), chunk("IHDR", header), chunk("IDAT", compressed.toByteArray()), chunk("IEND", new byte[0]));
        var inflated = assertThrows(IllegalArgumentException.class, () -> PngSanitizer.sanitize("a.png", "image/png", new ByteArrayInputStream(expansion)));
        assertEquals("Invalid PNG scanline length", inflated.getMessage());
    }
}
