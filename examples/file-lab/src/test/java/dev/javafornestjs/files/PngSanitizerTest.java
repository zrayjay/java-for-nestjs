package dev.javafornestjs.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
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
}
