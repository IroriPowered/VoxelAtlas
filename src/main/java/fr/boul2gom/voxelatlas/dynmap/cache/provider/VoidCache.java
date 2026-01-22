package fr.boul2gom.voxelatlas.dynmap.cache.provider;

import fr.boul2gom.voxelatlas.dynmap.cache.CacheType;
import fr.boul2gom.voxelatlas.dynmap.cache.TileCache;
import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder;

import java.util.concurrent.CompletableFuture;

/**
 * A dummy implementation of the {@link TileCache} interface that does nothing.
 * <p>
 * This cache implementation discards all stored tiles and always returns null
 * for retrievals.
 * It effectively disables caching.
 * </p>
 */
public class VoidCache implements TileCache {
    /**
     * Retrieves a tile from the cache.
     * <p>
     * This implementation always returns null.
     * </p>
     *
     * @param world    The name of the world.
     * @param zoom     The zoom level of the tile.
     * @param x        The X coordinate of the tile.
     * @param z        The Z coordinate of the tile.
     * @param format   The image format of the tile.
     * @param renderer The name of the renderer.
     * @return Always null.
     */
    @Override
    public CompletableFuture<byte[]> get(String world, int zoom, int x, int z, ImageEncoder.Format format, String renderer) {
        return null;
    }

    /**
     * Stores a tile in the cache.
     * <p>
     * This implementation effectively discards the data.
     * </p>
     *
     * @param world    The name of the world.
     * @param zoom     The zoom level of the tile.
     * @param x        The X coordinate of the tile.
     * @param z        The Z coordinate of the tile.
     * @param format   The image format of the tile.
     * @param renderer The name of the renderer.
     * @param data     The byte array containing the tile image data.
     */
    @Override
    public void put(String world, int zoom, int x, int z, ImageEncoder.Format format, String renderer, byte[] data) {

    }

    /**
     * Checks if a tile exists in the cache.
     *
     * @param world    The name of the world.
     * @param zoom     The zoom level of the tile.
     * @param x        The X coordinate of the tile.
     * @param z        The Z coordinate of the tile.
     * @param format   The image format of the tile.
     * @param renderer The name of the renderer.
     * @return Always false.
     */
    @Override
    public boolean has(String world, int zoom, int x, int z, ImageEncoder.Format format, String renderer) {
        return false;
    }

    /**
     * Initializes the cache.
     * <p>
     * This implementation does nothing.
     * </p>
     */
    @Override
    public void init() {

    }

    /**
     * Closes the cache.
     * <p>
     * This implementation does nothing.
     * </p>
     */
    @Override
    public void close() {

    }

    /**
     * Purges expired tiles from the cache.
     * <p>
     * This implementation does nothing.
     * </p>
     *
     * @param max_age_ms The maximum age of a tile in milliseconds (ignored).
     */
    @Override
    public void purge_expired(long max_age_ms) {

    }

    /**
     * Returns the type of this cache implementation.
     *
     * @return Always null as this is a void cache.
     */
    @Override
    public CacheType type() {
        return null;
    }
}
