package fr.boul2gom.voxelatlas.dynmap.renderer;

import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder.Format;
import java.util.concurrent.CompletableFuture;

/**
 * Interface determining how a map tile is rendered.
 * <p>
 * This interface defines the contract for rendering a specific tile
 * based on world coordinates and zoom level.
 * </p>
 */
public interface Renderer {
    /**
     * Renders a tile for the given coordinates and zoom level.
     *
     * @param world_name The name of the world to render from. Cannot be null.
     * @param x          The X coordinate of the tile.
     * @param z          The Z coordinate of the tile.
     * @param zoom       The zoom level of the tile.
     * @param format     The desired image format. Cannot be null.
     * @return A {@link CompletableFuture} containing the rendered image data as a
     *         byte array,
     *         or completing with null if rendering fails or the area is invalid.
     */
    CompletableFuture<byte[]> render(String world_name, int x, int z, int zoom, Format format);
}
