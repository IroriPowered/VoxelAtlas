package fr.boul2gom.voxelatlas.dynmap.cache.provider;

import fr.boul2gom.voxelatlas.VoxelAtlas;
import fr.boul2gom.voxelatlas.dynmap.cache.CacheType;
import fr.boul2gom.voxelatlas.dynmap.cache.TileCache;
import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder.Format;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import java.io.File;
import java.util.concurrent.CompletableFuture;

/**
 * A MapDB-based implementation of the {@link TileCache} interface.
 * <p>
 * This class handles tile storage using an embedded MapDB database.
 * It provides high-performance access to tiles stored in a single file.
 * </p>
 * <p>
 * Constructor parameters:
 * <ul>
 * <li><b>plugin</b>: The main VoxelAtlas plugin instance, used to locate the
 * data folder.</li>
 * <li><b>database_name</b>: The name of the database file within the plugin
 * data folder.</li>
 * </ul>
 * </p>
 */
public class MapDBTileCache implements TileCache {

    /** The database file path */
    private final File db_file;
    /** The MapDB database instance */
    private DB database;
    /** The HTreeMap storing the tiles */
    private HTreeMap<String, byte[]> map;

    /**
     * Create a new cache with MapDB backend.
     *
     * @param plugin        The main VoxelAtlas plugin instance, used to locate the
     *                      data folder.
     * @param database_name The name of the database file within the plugin data
     *                      folder.
     */
    public MapDBTileCache(VoxelAtlas plugin, String database_name) {
        this.db_file = plugin.data_directory().resolve(database_name).toFile();
    }

    /**
     * Initialize the cache by opening or creating the MapDB database.
     * <p>
     * This method configures the database with mmap enabled and sets up the hash
     * map for tile storage.
     * </p>
     */
    @Override
    public void init() {
        this.database = DBMaker.fileDB(db_file)
                .fileMmapEnable()
                .checksumHeaderBypass()
                .closeOnJvmShutdown()
                .make();

        this.map = database.hashMap("tiles")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.BYTE_ARRAY)
                .createOrOpen();
    }

    /**
     * Generates a unique key for storing or retrieving a tile.
     *
     * @param world    The name of the world. Cannot be null.
     * @param zoom     The zoom level of the tile.
     * @param x        The X coordinate of the tile.
     * @param z        The Z coordinate of the tile.
     * @param format   The image format of the tile. Cannot be null.
     * @param renderer The name of the renderer. Cannot be null.
     * @return A unique string key for the tile.
     */
    private String getKey(String world, int zoom, int x, int z, Format format, String renderer) {
        return world + "/" + zoom + "/" + x + "_" + z + "_" + renderer + "." + format.extension();
    }

    /**
     * Retrieves a tile asynchronously from the cache.
     *
     * @param world    The name of the world. Cannot be null.
     * @param zoom     The zoom level of the tile.
     * @param x        The X coordinate of the tile.
     * @param z        The Z coordinate of the tile.
     * @param format   The image format of the tile. Cannot be null.
     * @param renderer The name of the renderer. Cannot be null.
     * @return A {@link CompletableFuture} containing the tile data as a byte array.
     *         The future will complete with null if the tile is not found.
     */
    @Override
    public CompletableFuture<byte[]> get(String world, int zoom, int x, int z, Format format, String renderer) {
        return CompletableFuture.supplyAsync(() -> this.map.get(getKey(world, zoom, x, z, format, renderer)));
    }

    /**
     * Stores a tile in the cache map.
     * <p>
     * This method puts the data in the map and commits the transaction to the
     * database.
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
        this.map.put(getKey(world, zoom, x, z, format, renderer), data);
        // Commit might be expensive, maybe do it periodically or rely
        // on auto-commit if enabled (not enabled by default)
        // For now, let's commit occasionally or on close.
        this.database.commit();
    }

    /**
     * Checks if a tile exists in the cache map.
     *
     * @param world    The name of the world. Cannot be null.
     * @param zoom     The zoom level of the tile.
     * @param x        The X coordinate of the tile.
     * @param z        The Z coordinate of the tile.
     * @param format   The image format of the tile. Cannot be null.
     * @param renderer The name of the renderer. Cannot be null.
     * @return True if the tile exists in the database, false otherwise.
     */
    @Override
    public boolean has(String world, int zoom, int x, int z, Format format, String renderer) {
        return this.map.containsKey(getKey(world, zoom, x, z, format, renderer));
    }

    /**
     * Closes the MapDB database.
     */
    @Override
    public void close() {
        if (this.database != null && !this.database.isClosed()) {
            this.database.close();
        }
    }

    /**
     * Purges expired tiles from the cache.
     * <p>
     * <b>Note:</b> This implementation currently does not support expiration and
     * performs no operation.
     * MapDB does not natively track entry timestamps.
     * </p>
     *
     * @param maxAgeMillis The maximum age of a tile in milliseconds (ignored).
     */
    @Override
    public void purge_expired(long maxAgeMillis) {
        // MapDB doesn't track timestamps by default unless we store them in the value.
        // For now, no-op or we would need to change value schema.
        // Since the prompt asks for MapDB OR FileSystem, and FS was prioritized for
        // ZeroCopy,
        // and MapDB is option A (perf), maybe expiration is less critical here or needs
        // value object.
        // We will skip complex expiration for now to keep it simple as per plan.
    }

    /**
     * Returns the type of this cache implementation.
     *
     * @return The {@link CacheType#MAPDB} enum value.
     */
    @Override
    public CacheType type() {
        return CacheType.MAPDB;
    }
}
