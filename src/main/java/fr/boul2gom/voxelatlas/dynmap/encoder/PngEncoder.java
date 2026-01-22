package fr.boul2gom.voxelatlas.dynmap.encoder;

import com.hypixel.hytale.protocol.packets.worldmap.MapImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Utility class for encoding map images to PNG format.
 * <p>
 * This class handles the low-level details of converting raw pixel data into a
 * PNG file
 * using {@link ImageIO}. It supports resizing the image during encoding.
 * </p>
 */
public class PngEncoder {

    /**
     * Encodes a MapImage to PNG format with resizing.
     *
     * @param image       The {@link MapImage} containing raw pixel data. Cannot be
     *                    null.
     * @param output_size The desired output width/height in pixels. The image will
     *                    be scaled to this size.
     * @return A byte array containing the encoded PNG data. Returns an empty array
     *         on error.
     */
    public static byte[] encode(MapImage image, int output_size) {
        final int src_width = image.width;
        final int src_height = image.height;
        int[] data = image.data;

        final BufferedImage buffered = new BufferedImage(output_size, output_size, 2);

        float scaleX = (float) src_width / (float) output_size;
        float scaleY = (float) src_height / (float) output_size;
        for (int y = 0; y < output_size; ++y) {
            for (int x = 0; x < output_size; ++x) {
                if (data == null) {
                    data = new int[src_width * src_height];
                    image.data = data;
                }

                final int src_x = Math.min((int) ((float) x * scaleX), src_width - 1);
                final int src_y = Math.min((int) ((float) y * scaleY), src_height - 1);
                final int src_index = src_y * src_width + src_x;

                final int rgba = data[src_index];
                final int r = rgba >> 24 & 0xFF;
                final int g = rgba >> 16 & 0xFF;
                final int b = rgba >> 8 & 0xFF;
                final int a = rgba & 0xFF;

                final int argb = a << 24 | r << 16 | g << 8 | b;
                buffered.setRGB(x, y, argb);
            }
        }

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write((RenderedImage) buffered, "png", out);
        } catch (IOException e) {
            return new byte[0];
        }

        return out.toByteArray();
    }

    /**
     * Creates an empty (transparent) PNG image.
     *
     * @param size The width/height of the image in pixels.
     * @return A byte array containing the encoded empty PNG data. Returns an empty
     *         array on error.
     */
    public static byte[] empty(int size) {
        final BufferedImage buffered = new BufferedImage(size, size, 2);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            ImageIO.write((RenderedImage) buffered, "png", out);
        } catch (IOException e) {
            return new byte[0];
        }

        return out.toByteArray();
    }
}
