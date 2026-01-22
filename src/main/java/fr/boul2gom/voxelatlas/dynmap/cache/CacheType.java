package fr.boul2gom.voxelatlas.dynmap.cache;

import fr.boul2gom.voxelatlas.VoxelAtlas;
import fr.boul2gom.voxelatlas.dynmap.cache.provider.FileSystemTileCache;
import fr.boul2gom.voxelatlas.dynmap.cache.provider.MapDBTileCache;
import fr.boul2gom.voxelatlas.dynmap.cache.provider.VoidCache;

/**
 * Enumeration of available tile cache types.
 * <p>
 * This enum defines the supported caching backends for the tile system.
 * </p>
 */
public enum CacheType {
    /** In-memory cache using a synchronized LRU map */
    MEMORY("memory"),
    /** Filesystem-based cache storing tiles as individual files */
    FILESYSTEM("filesystem"),
    /** MapDB-based cache storing tiles in a single database file */
    MAPDB("mapdb"),
    /** No caching dummy implementation */
    NONE("none");

    /** The unique identifier for the cache type */
    private final String id;

    /**
     * Create a new cache type.
     *
     * @param id The unique identifier string for this cache type.
     */
    CacheType(String id) {
        this.id = id;
    }

    /**
     * Gets the unique identifier for this cache type.
     *
     * @return The identifier string.
     */
    public String id() {
        return this.id;
    }

    /**
     * Creates a new persistent tile cache instance based on this type.
     *
     * @param plugin The VoxelAtlas plugin instance.
     * @param name   The name for the cache storage (directory or file name).
     * @return A new {@link TileCache} implementation. Returns {@link VoidCache} if
     *         the type does not support persistence or on error.
     */
    public TileCache create_persistent(VoxelAtlas plugin, String name) {
        return switch (this) {
            case FILESYSTEM -> new FileSystemTileCache(plugin, name);
            case MAPDB -> new MapDBTileCache(plugin, name + ".db");
            default -> {
                VoxelAtlas.LOGGER.atSevere().log("[VoxelAtlas] Failed to initialize persistent cache: " + this.id() + ".");
                VoxelAtlas.LOGGER.atSevere().log("[VoxelAtlas] Falling back to no persistent cache.");
                yield new VoidCache();
            }
        };
    }

    /**
     * Resolves a cache type from its identifier string.
     *
     * @param id The identifier string to look up.
     * @return The matching {@link CacheType}, or {@link CacheType#NONE} if not
     *         found.
     */
    public static CacheType from_id(String id) {
        return switch (id.toLowerCase()) {
            case "memory" -> MEMORY;
            case "filesystem" -> FILESYSTEM;
            case "mapdb" -> MAPDB;
            default -> NONE;
        };
    }
}
