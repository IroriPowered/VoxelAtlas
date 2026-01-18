package fr.boul2gom.voxelaltas.dynmap.encoder;

import com.hypixel.hytale.protocol.packets.worldmap.MapImage;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * WebP image encoder for map tiles
 * Provides better compression than PNG with minimal quality loss
 */
public class WebpEncoder {

    private static final float DEFAULT_QUALITY = 0.85f;
    private static final boolean WEBP_AVAILABLE;

    static {
        final Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
        WEBP_AVAILABLE = writers.hasNext();

        if (!WEBP_AVAILABLE) {
            System.err.println("[VoxelAtlas] WebP encoder not available. Please add webp-imageio dependency.");
            System.err.println("[VoxelAtlas] Falling back to PNG encoder for all requests.");
        } else {
            System.out.println("[VoxelAtlas] WebP encoder initialized successfully");
        }
    }

    /**
     * Encode a MapImage to WebP format with default quality
     * @param image The map image to encode
     * @param outputSize The desired output size (width and height)
     * @return WebP encoded byte array
     */
    public static byte[] encode(MapImage image, int outputSize) {
        return encode(image, outputSize, DEFAULT_QUALITY);
    }

    /**
     * Encode a MapImage to WebP format with custom quality
     * @param image The map image to encode
     * @param outputSize The desired output size (width and height)
     * @param quality Quality factor (0.0 to 1.0, higher is better)
     * @return WebP encoded byte array
     */
    public static byte[] encode(MapImage image, int outputSize, float quality) {
        if (!WEBP_AVAILABLE) {
            // Fallback to PNG if WebP is not available
            System.err.println("[VoxelAtlas] WebP not available, falling back to PNG");
            return PngEncoder.encode(image, outputSize);
        }

        int srcWidth = image.width;
        int srcHeight = image.height;
        int[] data = image.data;

        // Create BufferedImage with ARGB format
        final BufferedImage buffered = new BufferedImage(outputSize, outputSize, BufferedImage.TYPE_INT_ARGB);

        // Scale and convert RGBA to ARGB
        float scaleX = (float) srcWidth / (float) outputSize;
        float scaleY = (float) srcHeight / (float) outputSize;

        for (int y = 0; y < outputSize; ++y) {
            for (int x = 0; x < outputSize; ++x) {
                int srcX = Math.min((int) ((float) x * scaleX), srcWidth - 1);
                int srcY = Math.min((int) ((float) y * scaleY), srcHeight - 1);
                int srcIndex = srcY * srcWidth + srcX;

                // Original data is in RGBA format
                int rgba = data[srcIndex];
                int r = (rgba >> 24) & 0xFF;
                int g = (rgba >> 16) & 0xFF;
                int b = (rgba >> 8) & 0xFF;
                int a = rgba & 0xFF;

                // Convert to ARGB format for BufferedImage
                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                buffered.setRGB(x, y, argb);
            }
        }

        return encodeBufferedImage(buffered, quality);
    }

    /**
     * Create an empty WebP image
     * @param size The size of the empty image (width and height)
     * @return WebP encoded byte array
     */
    public static byte[] empty(int size) {
        return empty(size, DEFAULT_QUALITY);
    }

    /**
     * Create an empty WebP image with custom quality
     * @param size The size of the empty image (width and height)
     * @param quality Quality factor (0.0 to 1.0)
     * @return WebP encoded byte array
     */
    public static byte[] empty(int size, float quality) {
        if (!WEBP_AVAILABLE) {
            return PngEncoder.empty(size);
        }

        BufferedImage buffered = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        return encodeBufferedImage(buffered, quality);
    }

    /**
     * Encode a BufferedImage to WebP format
     * @param image The BufferedImage to encode
     * @param quality Quality factor (0.0 to 1.0)
     * @return WebP encoded byte array
     */
    private static byte[] encodeBufferedImage(BufferedImage image, float quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            // Get WebP writer
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
            if (!writers.hasNext()) {
                System.err.println("[VoxelAtlas] No WebP writer found");
                return new byte[0];
            }

            ImageWriter writer = writers.next();

            // Configure compression parameters
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            if (writeParam.canWriteCompressed()) {
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionQuality(quality);
            }

            // Write the image
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), writeParam);
                writer.dispose();
            }

            return out.toByteArray();
        } catch (IOException e) {
            System.err.println("[VoxelAtlas] Failed to encode WebP: " + e.getMessage());
            return new byte[0];
        }
    }

    /**
     * Check if WebP encoding is available
     * @return true if WebP encoder is available, false otherwise
     */
    public static boolean isAvailable() {
        return WEBP_AVAILABLE;
    }

    /**
     * Get the default quality setting
     * @return The default quality factor
     */
    public static float getDefaultQuality() {
        return DEFAULT_QUALITY;
    }
}
