package fr.boul2gom.voxelatlas.dynmap;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import fr.boul2gom.voxelatlas.VoxelAtlas;
import fr.boul2gom.voxelatlas.dynmap.cache.CacheType;
import fr.boul2gom.voxelatlas.dynmap.cache.TileCache;
import fr.boul2gom.voxelatlas.dynmap.cache.provider.MemoryTileCache;
import fr.boul2gom.voxelatlas.dynmap.data.ChunkAccessor;
import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder.Format;
import fr.boul2gom.voxelatlas.dynmap.renderer.FlatRenderer;
import fr.boul2gom.voxelatlas.dynmap.renderer.Renderer;
import fr.boul2gom.voxelatlas.dynmap.renderer.RendererType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Core component responsible for managing map tile operations.
 * <p>
 * This class coordinates tile generation, caching, and retrieval.
 * It handles the flow between memory cache, persistent storage, and the
 * renderer.
 * </p>
 */
public class TileManager {

    /** The main plugin instance */
    private final VoxelAtlas plugin;
    /** Executor for fast, interactive tile requests */
    private final ExecutorService interactive_executor;
    /** Executor for slow background tasks */
    private final ExecutorService background_executor;

    /** A map of pending tile requests to prevent duplicate processing */
    private final ConcurrentHashMap<String, CompletableFuture<byte[]>> requests;
    /** The in-memory L1 cache */
    private final TileCache memory_cache;
    /** The persistent L2 cache (filesystem or database) */
    private final TileCache persistent_cache;

    /** The renderer implementation used to generate tiles */
    private final Renderer flat_renderer;

    /**
     * Create a new tile manager.
     * <p>
     * Initializes the caches (memory and persistent based on config) and the thread
     * pools.
     * </p>
     *
     * @param plugin The VoxelAtlas plugin instance.
     */
    public TileManager(VoxelAtlas plugin) {
        this.plugin = plugin;
        this.interactive_executor = Executors.newFixedThreadPool(4); // fast interactive response
        this.background_executor = Executors.newFixedThreadPool(1); // slow background tasks

        this.requests = new ConcurrentHashMap<>();

        this.memory_cache = new MemoryTileCache(500);

        // Configurable Persistent Cache
        final String cache_str = plugin.config().get().cache_type();
        final CacheType cache_type = CacheType.from_id(cache_str);

        this.persistent_cache = cache_type.create_persistent(plugin, "tiles");
        this.persistent_cache.init();

        this.flat_renderer = new FlatRenderer();

        VoxelAtlas.LOGGER.atInfo().log("[VoxelAtlas] TileManager initialized:");
        VoxelAtlas.LOGGER.atInfo().log("[VoxelAtlas]   - Persistent Cache: " + this.persistent_cache.type());
        VoxelAtlas.LOGGER.atInfo().log("[VoxelAtlas]   - Memory Cache: 500 items");
    }

    /**
     * Gets the active persistent cache instance.
     *
     * @return The {@link TileCache} used for persistent storage.
     */
    public TileCache persistent_cache() {
        return this.persistent_cache;
    }

    /**
     * Fetches a tile with the specified format and renderer settings.
     * <p>
     * This method applies a tiered cache strategy:
     * <ol>
     * <li>Check memory cache (L1).</li>
     * <li>Check persistent cache (L2).</li>
     * <li>Generate tile if missing (and update caches).</li>
     * </ol>
     * </p>
     *
     * @param world       The name of the world. Cannot be null.
     * @param zoom        The zoom level of the tile.
     * @param tile_x      The X coordinate of the tile.
     * @param tile_z      The Z coordinate of the tile.
     * @param format      The desired image format. Cannot be null.
     * @param renderer    The renderer type to use. Cannot be null.
     * @param interactive Whether this request is interactive (higher priority) or
     *                    background.
     * @param force       If true, bypasses checks for unexplored chunks and forces
     *                    generation.
     * @return A {@link CompletableFuture} containing the tile image data.
     */
    public CompletableFuture<byte[]> fetch_tile(String world, int zoom, int tile_x, int tile_z, Format format,
            RendererType renderer, boolean interactive, boolean force) {
        final String key = this.create_key(world, zoom, tile_x, tile_z, format, renderer);

        // 1. Check Memory Cache
        return this.memory_cache.get(world, zoom, tile_x, tile_z, format, renderer.name())
                .thenCompose(memory_data -> {
                    if (memory_data != null && memory_data.length > 0) {
                        return CompletableFuture.completedFuture(memory_data);
                    }

                    // 2. Check Persistent Cache
                    return this.persistent_cache.get(world, zoom, tile_x, tile_z, format, renderer.name())
                            .thenCompose(persistent_data -> {
                                if (persistent_data != null && persistent_data.length > 0) {
                                    this.memory_cache.put(world, zoom, tile_x, tile_z, format, renderer.name(),
                                            persistent_data);
                                    return CompletableFuture.completedFuture(persistent_data);
                                }

                                // 3. Generate
                                final CompletableFuture<byte[]> pending = this.requests.get(key);
                                if (pending != null) {
                                    return pending;
                                }

                                final CompletableFuture<byte[]> future = this.generate_tile(world, zoom, tile_x, tile_z,
                                        format, renderer, interactive, force);
                                this.requests.put(key, future);

                                future.whenComplete((res, ex) -> {
                                    this.requests.remove(key);
                                    if (res != null && ex == null && res.length > 0) {
                                        this.memory_cache.put(world, zoom, tile_x, tile_z, format, renderer.name(),
                                                res);
                                        this.persistent_cache.put(world, zoom, tile_x, tile_z, format, renderer.name(),
                                                res);

                                        this.broadcast_tile_update(world, tile_x, tile_z, zoom);
                                    } else {
                                        if (ex != null) {
                                            VoxelAtlas.LOGGER.atWarning().log("Tile generation failed for " + tile_x
                                                    + "," + tile_z + ": " + ex.getMessage());
                                        }
                                    }
                                });
                                return future;
                            });
                });
    }

    /**
     * Generates a new tile using the configured renderer.
     *
     * @param world_name  The name of the world to render.
     * @param zoom        The zoom level.
     * @param tile_x      The tile X coordinate.
     * @param tile_z      The tile Z coordinate.
     * @param format      The output image format.
     * @param renderer    The renderer type.
     * @param interactive Priority flag (unused here but passed for context).
     * @param force       Whether to force generation even for unexplored areas.
     * @return A {@link CompletableFuture} with the generated byte array.
     */
    private CompletableFuture<byte[]> generate_tile(String world_name, int zoom, int tile_x, int tile_z, Format format,
            RendererType renderer, boolean interactive, boolean force) {
        // Safety check using ChunkAccessor to prevent unwanted generation
        if (zoom == 0) {
            final World world = Universe.get().getWorld(world_name);
            if (world != null) {
                ChunkAccessor accessor = new ChunkAccessor(world);

                // If NOT forced, check unexplored
                if (!force) {
                    return accessor.is_unexplored(tile_x, tile_z).thenCompose(unexplored -> {
                        if (unexplored)
                            return CompletableFuture.completedFuture(null);

                        // Use the renderer (which runs async internally usually)
                        return this.flat_renderer.render(world_name, tile_x, tile_z, zoom, format);
                    });
                }
            }
        }

        // Use the renderer (which runs async internally usually)
        return this.flat_renderer.render(world_name, tile_x, tile_z, zoom, format);
    }

    /**
     * Pre-generates tiles for a specified area relative to the spawn or center.
     *
     * @param world_name The name of the world.
     * @param center_x   The center X chunk coordinate.
     * @param center_z   The center Z chunk coordinate.
     * @param radius     The radius in chunks to pregenerate.
     * @param format     The image format to use.
     * @return A {@link CompletableFuture} that completes with the number of
     *         generated tiles.
     */
    public CompletableFuture<Integer> pregenerate(String world_name, int center_x, int center_z, int radius,
            Format format) {
        final World world = Universe.get().getWorld(world_name);
        if (world == null)
            return CompletableFuture.completedFuture(0);

        return CompletableFuture.supplyAsync(() -> {
            int count = 0;

            for (int x = center_x - radius; x <= center_x + radius; ++x) {
                for (int z = center_z - radius; z <= center_z + radius; ++z) {
                    try {
                        final byte[] tile = this.fetch_tile(world_name, 0, x, z, format, RendererType.FLAT, false, true)
                                .join();

                        if (tile != null && tile.length > 0) {
                            ++count;
                        }
                    } catch (Exception e) {
                        VoxelAtlas.LOGGER.atSevere().log(
                                "[VoxelAtlas] - Failed to pregenerate tile (" + x + ", " + z + "): " + e.getMessage());
                    }
                }
            }

            return count;
        });
    }

    /**
     * Creates a unique cache key for a tile request.
     *
     * @param world    The world name.
     * @param zoom     The zoom level.
     * @param x        The tile X coordinate.
     * @param z        The tile Z coordinate.
     * @param format   The image format.
     * @param renderer The renderer type.
     * @return A slash-separated key string.
     */
    public String create_key(String world, int zoom, int x, int z, Format format, RendererType renderer) {
        return world + "/" + zoom + "/" + x + "/" + z + "/" + format.extension() + "/" + renderer.id();
    }

    /**
     * Purges expired tiles from the persistent cache.
     *
     * @param max_age_ms The maximum age in milliseconds.
     */
    public void purge_expired(long max_age_ms) {
        if (this.persistent_cache != null) {
            this.persistent_cache.purge_expired(max_age_ms);
        }
    }

    /**
     * Shuts down the tile manager and releases all resources.
     * <p>
     * Closes caches and shuts down executor services.
     * </p>
     */
    public void close() {
        if (this.persistent_cache != null) {
            this.persistent_cache.close();
        }
        if (this.memory_cache != null) {
            this.memory_cache.close();
        }
        if (this.interactive_executor != null) {
            this.interactive_executor.shutdown();
        }
        if (this.background_executor != null) {
            this.background_executor.shutdown();
        }
    }

    /**
     * Triggers updates for a spiral of tiles around a center point.
     * <p>
     * Used to refresh the map view around a player or event.
     * </p>
     *
     * @param world_name The world name.
     * @param center_x   The center chunk X.
     * @param center_z   The center chunk Z.
     * @param radius     The radius in chunks.
     */
    public void update_tiles_around(String world_name, int center_x, int center_z, int radius) {
        final World world = Universe.get().getWorld(world_name);
        if (world == null)
            return;

        // Simple spiral iterator implementation
        int x = 0;
        int z = 0;
        int dx = 0;
        int dz = -1;

        // Max steps for a square of side 2*radius + 1
        final int max_steps = (2 * radius + 1) * (2 * radius + 1);

        for (int i = 0; i < max_steps; i++) {
            if ((-radius <= x) && (x <= radius) && (-radius <= z) && (z <= radius)) {
                this.fetch_tile(world_name, 0, center_x + x, center_z + z, Format.PNG, RendererType.FLAT, false, false);
            }

            if ((x == z) || ((x < 0) && (x == -z)) || ((x > 0) && (x == 1 - z))) {
                final int temporary = dx;
                dx = -dz;
                dz = temporary;
            }

            x += dx;
            z += dz;
        }
    }

    /**
     * Broadcasts a tile update event to all connected WebSocket clients.
     *
     * @param world  The world name.
     * @param tile_x The tile X coordinate.
     * @param tile_z The tile Z coordinate.
     * @param zoom   The zoom level.
     */
    public void broadcast_tile_update(String world, int tile_x, int tile_z, int zoom) {
        if (this.plugin.websocket() == null || this.plugin.websocket().connections_count() == 0) {
            return;
        }

        final com.google.gson.JsonObject message = new com.google.gson.JsonObject();
        message.addProperty("type", "tile_update");
        message.addProperty("world", world);
        message.addProperty("x", tile_x);
        message.addProperty("z", tile_z);
        message.addProperty("zoom", zoom);

        this.plugin.websocket().broadcast(message);
    }
}
