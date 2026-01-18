package fr.boul2gom.hymap.dynmap.encoder;

import com.hypixel.hytale.protocol.packets.worldmap.MapImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PngEncoder {

    public static byte[] encode(MapImage image, int outputSize) {
        int srcWidth = image.width;
        int srcHeight = image.height;
        int[] data = image.data;

        final BufferedImage buffered = new BufferedImage(outputSize, outputSize, 2);

        float scaleX = (float)srcWidth / (float)outputSize;
        float scaleY = (float)srcHeight / (float)outputSize;
        for (int y = 0; y < outputSize; ++y) {
            for (int x = 0; x < outputSize; ++x) {
                int srcX = Math.min((int)((float)x * scaleX), srcWidth - 1);
                int srcY = Math.min((int)((float)y * scaleY), srcHeight - 1);
                int srcIndex = srcY * srcWidth + srcX;
                int rgba = data[srcIndex];
                int r = rgba >> 24 & 0xFF;
                int g = rgba >> 16 & 0xFF;
                int b = rgba >> 8 & 0xFF;
                int a = rgba & 0xFF;
                int argb = a << 24 | r << 16 | g << 8 | b;
                buffered.setRGB(x, y, argb);
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write((RenderedImage)buffered, "png", out);
        } catch (IOException e) {
            return new byte[0];
        }

        return out.toByteArray();
    }

    public static byte[] empty(int size) {
        BufferedImage buffered = new BufferedImage(size, size, 2);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            ImageIO.write((RenderedImage)buffered, "png", out);
        } catch (IOException e) {
            return new byte[0];
        }

        return out.toByteArray();
    }
}
