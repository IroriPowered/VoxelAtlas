package fr.boul2gom.voxelatlas.dynmap.cache.provider;

import fr.boul2gom.voxelatlas.dynmap.cache.CacheType;
import fr.boul2gom.voxelatlas.dynmap.cache.TileCache;
import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder.Format;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A memory-based implementation of the {@link TileCache} interface.
 * <p>
 * This class stores tiles in an in-memory LRU (Least Recently Used) cache.
 * It is useful for temporary storage and fast access but is limited by the
 * available heap memory.
 * </p>
 * <p>
 * Constructor parameters:
 * <ul>
 * <li><b>capacity</b>: The maximum number of tiles the cache can hold before
 * evicting the oldest ones.</li>
 * </ul>
 * </p>
 */
public class MemoryTileCache implements TileCache {

    /** The synchronized map backing the cache */
    private final Map<String, byte[]> cache;
    /** The maximum capacity of the cache */
    private final int capacity;

    /**
     * Create a new cache with memory backend.
     *
     * @param capacity The maximum number of tiles the cache can hold before
     *                 evicting the oldest ones.
     */
    public MemoryTileCache(int capacity) {
        this.capacity = capacity;
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                return size() > MemoryTileCache.this.capacity;
            }
        });
    }

    /**
     * Retrieves a tile from the memory cache.
     *
     * @param world    The name of the world. Cannot be null.
     * @param zoom     The zoom level of the tile.
     * @param x        The X coordinate of the tile.
     * @param z        The Z coordinate of the tile.
     * @param format   The image format of the tile. Cannot be null.
     * @param renderer The name of the renderer. Cannot be null.
     * @return A {@link CompletableFuture} containing the tile data as a byte array.
     *         The future will complete with null if the tile is not in the cache.
     */
    @Override
    public CompletableFuture<byte[]> get(String world, int zoom, int x, int z, Format format, String renderer) {
        final String key = create_key(world, zoom, x, z, format, renderer);
        final byte[] data = this.cache.get(key);
        if (data != null) {
            return CompletableFuture.completedFuture(data);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Stores a tile in the memory cache.
     * <p>
     * If the cache is full, the least recently used item will be evicted.
     * </p>
     *
     * @param world    The name of the world. Cannot be null.
     * @param zoom     The zoom level of the tile.
     * @param x        The X coordinate of the tile.
     * @param z        The Z coordinate of the tile.
     * @param format   The image format of the tile. Cannot be null.
     * @param renderer The name of the renderer. Cannot be null.
     * @param data     The byte array containing the tile image data. Can be null.
     */
    @Override
    public void put(String world, int zoom, int x, int z, Format format, String renderer, byte[] data) {
        final String key = create_key(world, zoom, x, z, format, renderer);
        this.cache.put(key, data);
    }

    /**
     * Checks if a tile exists in the memory cache.
     *
     * @param world    The name of the world. Cannot be null.
     * @param zoom     The zoom level of the tile.
     * @param x        The X coordinate of the tile.
     * @param z        The Z coordinate of the tile.
     * @param format   The image format of the tile. Cannot be null.
     * @param renderer The name of the renderer. Cannot be null.
     * @return True if the tile is present in the cache, false otherwise.
     */
    @Override
    public boolean has(String world, int zoom, int x, int z, Format format, String renderer) {
        return this.cache.containsKey(create_key(world, zoom, x, z, format, renderer));
    }

    /**
     * Initializes the cache.
     * <p>
     * No specific initialization is required for the memory cache.
     * </p>
     */
    @Override
    public void init() {
        // No initialization needed for memory cache
    }

    /**
     * Clears all entries from the memory cache.
     */
    @Override
    public void close() {
        cache.clear();
    }

    /**
     * Purges expired tiles from the cache.
     * <p>
     * <b>Note:</b> The memory cache uses an LRU eviction policy based on capacity,
     * not time-based expiration.
     * Therefore, this method does nothing.
     * </p>
     *
     * @param max_age_ms The maximum age of a tile in milliseconds (ignored).
     */
    @Override
    public void purge_expired(long max_age_ms) {
        // Memory cache doesn't support expiration by age, it uses LRU
    }

    /**
     * Returns the type of this cache implementation.
     *
     * @return The {@link CacheType#MEMORY} enum value.
     */
    @Override
    public CacheType type() {
        return CacheType.MEMORY;
    }

    /**
     * Creates a unique key string for the cache map.
     *
     * @param world    The name of the world.
     * @param zoom     The zoom level.
     * @param x        The X coordinate.
     * @param z        The Z coordinate.
     * @param format   The format of the image.
     * @param renderer The name of the renderer.
     * @return A colon-separated key string.
     */
    private String create_key(String world, int zoom, int x, int z, Format format, String renderer) {
        return world + ":" + zoom + ":" + x + ":" + z + ":" + format + ":" + renderer;
    }
}
