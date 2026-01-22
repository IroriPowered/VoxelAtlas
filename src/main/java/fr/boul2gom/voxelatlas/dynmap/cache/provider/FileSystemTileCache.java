package fr.boul2gom.voxelatlas.dynmap.cache.provider;

import fr.boul2gom.voxelatlas.VoxelAtlas;
import fr.boul2gom.voxelatlas.dynmap.cache.CacheType;
import fr.boul2gom.voxelatlas.dynmap.cache.TileCache;
import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder.Format;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * A filesystem-based implementation of the {@link TileCache} interface.
 * <p>
 * This class handles tile storage by writing files to a specified directory on
 * the disk.
 * It uses a dedicated thread pool for IO operations to avoid blocking the main
 * thread.
 * </p>
 * <p>
 * Constructor parameters:
 * <ul>
 * <li><b>plugin</b>: The main VoxelAtlas plugin instance, used to locate the
 * data folder.</li>
 * <li><b>dir_name</b>: The name of the subdirectory within the plugin data
 * folder where tiles are stored.</li>
 * </ul>
 * </p>
 */
public class FileSystemTileCache implements TileCache {

    /** The root directory for the tile cache */
    private final Path directory;
    /** The dedicated executor service for asynchronous IO operations */
    private final ExecutorService io_executor;

    /**
     * Create a new cache with filesystem backend.
     *
     * @param plugin   The main VoxelAtlas plugin instance, used to locate the data
     *                 folder.
     * @param dir_name The name of the subdirectory within the plugin data folder
     *                 where tiles are stored.
     */
    public FileSystemTileCache(VoxelAtlas plugin, String dir_name) {
        this.directory = plugin.data_directory().resolve(dir_name);
        this.io_executor = Executors.newFixedThreadPool(4); // IO Pool
    }

    /**
     * Initialize the cache by creating the necessary directories.
     * <p>
     * This method attempts to create the root directory for the cache.
     * If directory creation fails, a warning is logged.
     * </p>
     */
    @Override
    public void init() {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            VoxelAtlas.LOGGER.atWarning().log("Failed to create tile cache directory: " + e.getMessage());
        }
    }

    /**
     * Resolves the filesystem path for a specific tile based on its coordinates and
     * properties.
     *
     * @param world    The name of the world. Cannot be null.
     * @param zoom     The zoom level of the tile.
     * @param x        The X coordinate of the tile.
     * @param z        The Z coordinate of the tile.
     * @param format   The image format of the tile. Cannot be null.
     * @param renderer The name of the renderer. Cannot be null.
     * @return The resolved {@link Path} to the file. Never returns null.
     */
    public Path get_file(String world, int zoom, int x, int z, Format format, String renderer) {
        return this.directory
                .resolve(renderer.toLowerCase())
                .resolve(world)
                .resolve(String.valueOf(zoom))
                .resolve(x + "_" + z + "." + format.extension());
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
     *         The future will complete with null if the file does not exist or if
     *         an {@link IOException} occurs.
     */
    @Override
    public CompletableFuture<byte[]> get(String world, int zoom, int x, int z, Format format, String renderer) {
        return CompletableFuture.supplyAsync(() -> {
            final Path file = this.get_file(world, zoom, x, z, format, renderer);
            if (Files.exists(file)) {
                try {
                    return Files.readAllBytes(file);
                } catch (IOException e) {
                    VoxelAtlas.LOGGER.atWarning().log("Failed to read tile file: " + file.toAbsolutePath());
                    return null;
                }
            }
            return null;
        }, this.io_executor);
    }

    /**
     * Stores a tile in the cache asynchronously.
     * <p>
     * If the data is null or empty, the operation is ignored.
     * </p>
     *
     * @param world    The name of the world. Cannot be null.
     * @param zoom     The zoom level of the tile.
     * @param x        The X coordinate of the tile.
     * @param z        The Z coordinate of the tile.
     * @param format   The image format of the tile. Cannot be null.
     * @param renderer The name of the renderer. Cannot be null.
     * @param data     The byte array containing the tile image data. Can be null
     *                 (ignored).
     */
    @Override
    public void put(String world, int zoom, int x, int z, Format format, String renderer, byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }

        this.io_executor.submit(() -> {
            try {
                final Path file = this.get_file(world, zoom, x, z, format, renderer);

                Files.createDirectories(file.getParent());
                Files.write(file, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                VoxelAtlas.LOGGER.atWarning().log("Failed to write tile file: " + e.getMessage());
            }
        });
    }

    /**
     * Checks if a tile exists in the cache.
     *
     * @param world    The name of the world. Cannot be null.
     * @param zoom     The zoom level of the tile.
     * @param x        The X coordinate of the tile.
     * @param z        The Z coordinate of the tile.
     * @param format   The image format of the tile. Cannot be null.
     * @param renderer The name of the renderer. Cannot be null.
     * @return True if the tile file exists on the filesystem, false otherwise.
     */
    @Override
    public boolean has(String world, int zoom, int x, int z, Format format, String renderer) {
        return Files.exists(this.get_file(world, zoom, x, z, format, renderer));
    }

    /**
     * Closes the cache and shuts down the IO executor service.
     */
    @Override
    public void close() {
        this.io_executor.shutdown();
    }

    /**
     * Purges expired tiles from the cache asynchronously.
     * <p>
     * Tiles strictly older than the specified max age will be deleted.
     * </p>
     *
     * @param max_age_ms The maximum age of a tile in milliseconds. Tiles older than
     *                   this will be removed.
     */
    @Override
    public void purge_expired(long max_age_ms) {
        this.io_executor.submit(() -> {
            try (final Stream<Path> walk = Files.walk(this.directory)) {
                final long now = System.currentTimeMillis();
                walk.filter(Files::isRegularFile)
                        .forEach(path -> {
                            try {
                                if (now - Files.getLastModifiedTime(path).toMillis() > max_age_ms) {
                                    Files.delete(path);
                                }
                            } catch (IOException _) {}
                        });
            } catch (IOException e) {
                VoxelAtlas.LOGGER.atWarning().log("Failed to purge expired tiles: " + e.getMessage());
            }
        });
    }

    /**
     * Returns the type of this cache implementation.
     *
     * @return The {@link CacheType#FILESYSTEM} enum value.
     */
    @Override
    public CacheType type() {
        return CacheType.FILESYSTEM;
    }
}
