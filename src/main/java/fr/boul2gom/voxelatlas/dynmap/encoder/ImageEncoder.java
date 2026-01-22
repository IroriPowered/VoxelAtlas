package fr.boul2gom.voxelatlas.dynmap.encoder;

import com.hypixel.hytale.protocol.packets.worldmap.MapImage;

/**
 * Image encoder interface for map tiles.
 * <p>
 * This class provides a centralized interface for encoding map images into
 * various formats
 * (currently supporting PNG). It handles format selection and delegates to
 * specific encoder implementations.
 * </p>
 */
public class ImageEncoder {

    /**
     * Enumeration of supported image formats.
     */
    public enum Format {
        /** PNG format (image/png) */
        PNG("image/png", "png");

        /** The MIME type string */
        private final String mime;
        /** The file extension string */
        private final String extension;

        /**
         * Create a new format.
         *
         * @param mime      The MIME type of the format.
         * @param extension The file extension of the format.
         */
        Format(String mime, String extension) {
            this.mime = mime;
            this.extension = extension;
        }

        /**
         * Gets the MIME type of this format.
         *
         * @return The MIME type string.
         */
        public String mime_type() {
            return this.mime;
        }

        /**
         * Gets the file extension of this format.
         *
         * @return The file extension string.
         */
        public String extension() {
            return this.extension;
        }

        /**
         * Parses a format from a string.
         *
         * @param format The format name string (e.g., "png", "webp").
         * @return The matching {@link Format} enum value, or defaults to
         *         {@link Format#PNG} if unknown.
         */
        public static Format from_format(String format) {
            return PNG;
        }
    }

    /**
     * Encodes a MapImage to the specified format.
     *
     * @param image       The {@link MapImage} to encode. Cannot be null.
     * @param output_size The desired output width/height in pixels.
     * @param format      The target {@link Format}. Cannot be null.
     * @return A byte array containing the encoded image data.
     */
    public static byte[] encode(MapImage image, int output_size, Format format) {
        return switch (format) {
            case PNG -> PngEncoder.encode(image, output_size);
        };
    }

    /**
     * Encodes a MapImage to PNG format (default).
     *
     * @param image       The {@link MapImage} to encode. Cannot be null.
     * @param output_size The desired output width/height in pixels.
     * @return A byte array containing the encoded PNG data.
     */
    public static byte[] encode(MapImage image, int output_size) {
        return encode(image, output_size, Format.PNG);
    }

    /**
     * Creates an empty (transparent) image in the specified format.
     *
     * @param size   The width/height of the empty image in pixels.
     * @param format The target {@link Format}. Cannot be null.
     * @return A byte array containing the encoded empty image.
     */
    public static byte[] empty(int size, Format format) {
        return switch (format) {
            case PNG -> PngEncoder.empty(size);
        };
    }

    /**
     * Creates an empty (transparent) PNG image (default).
     *
     * @param size The width/height of the empty image in pixels.
     * @return A byte array containing the encoded empty PNG.
     */
    public static byte[] empty(int size) {
        return empty(size, Format.PNG);
    }

    /**
     * Gets the MIME type string for a given format.
     *
     * @param format The {@link Format} to get the MIME type for.
     * @return The MIME type string (e.g., "image/png").
     */
    public static String mime_type(Format format) {
        return format.mime_type();
    }
}
