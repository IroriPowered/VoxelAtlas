package fr.boul2gom.voxelatlas.dynmap.encoder;

import com.hypixel.hytale.protocol.packets.worldmap.MapImage;

/**
 * Image encoder interface for map tiles
 * Supports multiple image formats (PNG, WebP)
 */
public class ImageEncoder {

    /**
     * Image format enum
     */
    public enum Format {
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        private final String mimeType;
        private final String extension;

        Format(String mimeType, String extension) {
            this.mimeType = mimeType;
            this.extension = extension;
        }

        public String getMimeType() {
            return mimeType;
        }

        public String getExtension() {
            return extension;
        }

        /**
         * Parse format from string
         * @param format Format string (png, webp)
         * @return Format enum, defaults to PNG if unknown
         */
        public static Format fromString(String format) {
            if (format == null) {
                return PNG;
            }

            try {
                return Format.valueOf(format.toUpperCase());
            } catch (IllegalArgumentException e) {
                return PNG;
            }
        }
    }

    /**
     * Encode a MapImage to the specified format
     * @param image The map image to encode
     * @param outputSize The desired output size
     * @param format The image format
     * @return Encoded byte array
     */
    public static byte[] encode(MapImage image, int outputSize, Format format) {
        return switch (format) {
            case WEBP -> WebpEncoder.isAvailable()
                ? WebpEncoder.encode(image, outputSize)
                : PngEncoder.encode(image, outputSize);
            case PNG -> PngEncoder.encode(image, outputSize);
        };
    }

    /**
     * Encode a MapImage to PNG format (default)
     * @param image The map image to encode
     * @param outputSize The desired output size
     * @return Encoded byte array
     */
    public static byte[] encode(MapImage image, int outputSize) {
        return encode(image, outputSize, Format.PNG);
    }

    /**
     * Create an empty image in the specified format
     * @param size The size of the empty image
     * @param format The image format
     * @return Encoded byte array
     */
    public static byte[] empty(int size, Format format) {
        return switch (format) {
            case WEBP -> WebpEncoder.isAvailable()
                ? WebpEncoder.empty(size)
                : PngEncoder.empty(size);
            case PNG -> PngEncoder.empty(size);
        };
    }

    /**
     * Create an empty PNG image (default)
     * @param size The size of the empty image
     * @return Encoded byte array
     */
    public static byte[] empty(int size) {
        return empty(size, Format.PNG);
    }

    /**
     * Get the MIME type for a format
     * @param format The image format
     * @return MIME type string
     */
    public static String getMimeType(Format format) {
        return format.getMimeType();
    }

    /**
     * Check if a format is available
     * @param format The image format to check
     * @return true if the format is available
     */
    public static boolean isFormatAvailable(Format format) {
        return switch (format) {
            case PNG -> true;
            case WEBP -> WebpEncoder.isAvailable();
        };
    }
}
