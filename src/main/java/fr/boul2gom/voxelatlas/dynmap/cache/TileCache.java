package fr.boul2gom.voxelatlas.dynmap.cache;

import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder.Format;

import java.util.concurrent.CompletableFuture;

/**
 * Interface defining the contract for tile caching implementations.
 * <p>
 * This interface abstracts the storage and retrieval of map tiles.
 * Implementations can provide various backends such as memory, filesystem, or
 * databases.
 * </p>
 */
public interface TileCache {

    /**
     * Retrieves a tile from the cache asynchronously.
     *
     * @param world    The name of the world. Cannot be null.
     * @param zoom     The zoom level of the tile.
     * @param x        The X coordinate of the tile.
     * @param z        The Z coordinate of the tile.
     * @param format   The image format of the tile. Cannot be null.
     * @param renderer The name of the renderer. Cannot be null.
     * @return A {@link CompletableFuture} containing the tile data as a byte array,
     *         or completing with null if the tile is not found.
     */
    CompletableFuture<byte[]> get(String world, int zoom, int x, int z, Format format, String renderer);

    /**
     * Stores a tile in the cache.
     *
     * @param world    The name of the world. Cannot be null.
     * @param zoom     The zoom level of the tile.
     * @param x        The X coordinate of the tile.
     * @param z        The Z coordinate of the tile.
     * @param format   The image format of the tile. Cannot be null.
     * @param renderer The name of the renderer. Cannot be null.
     * @param data     The byte array containing the tile image data. Can be null
     *                 (implementation specific behavior).
     */
    void put(String world, int zoom, int x, int z, Format format, String renderer, byte[] data);

    /**
     * Checks if a tile exists in the cache.
     *
     * @param world    The name of the world. Cannot be null.
     * @param zoom     The zoom level of the tile.
     * @param x        The X coordinate of the tile.
     * @param z        The Z coordinate of the tile.
     * @param format   The image format of the tile. Cannot be null.
     * @param renderer The name of the renderer. Cannot be null.
     * @return True if the tile exists in the cache, false otherwise.
     */
    boolean has(String world, int zoom, int x, int z, Format format, String renderer);

    /**
     * Initializes the cache.
     * <p>
     * This method should be called before using the cache to perform any necessary
     * setup,
     * such as creating directories or opening database connections.
     * </p>
     */
    void init();

    /**
     * Closes the cache and releases resources.
     * <p>
     * This method should be called when the cache is no longer needed.
     * </p>
     */
    void close();

    /**
     * Purges expired tiles from the cache.
     *
     * @param max_age_ms The maximum age of a tile in milliseconds. Tiles older than
     *                   this should be removed.
     */
    void purge_expired(long max_age_ms);

    /**
     * Gets the type of this cache implementation.
     *
     * @return The {@link CacheType} of this cache.
     */
    CacheType type();
}
