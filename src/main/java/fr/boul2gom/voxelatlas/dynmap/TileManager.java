package fr.boul2gom.voxelatlas.dynmap;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import fr.boul2gom.voxelatlas.VoxelAtlas;
import fr.boul2gom.voxelatlas.dynmap.cache.provider.MemoryTileCache;
import fr.boul2gom.voxelatlas.dynmap.cache.provider.SQLiteTileCache;
import fr.boul2gom.voxelatlas.dynmap.cache.TileCache;
import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder;
import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder.Format;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.provider.IndexedStorageChunkStorageProvider.IndexedStorageCache;
import com.hypixel.hytale.server.core.universe.world.storage.provider.IndexedStorageChunkStorageProvider.IndexedStorageChunkLoader;
import com.hypixel.hytale.storage.IndexedStorageFile;
import com.hypixel.hytale.component.Store;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.hypixel.hytale.math.vector.Vector3d;
import fr.boul2gom.voxelatlas.dynmap.data.WorldDataProvider;

public class TileManager {

    private final VoxelAtlas plugin;
    private final ExecutorService generation_executor;

    private final ConcurrentHashMap<String, CompletableFuture<byte[]>> requests;
    private final TileCache memory_cache;
    private final TileCache sqlite_cache;

    public TileManager(VoxelAtlas plugin) {
        this.plugin = plugin;
        this.generation_executor = Executors.newFixedThreadPool(4); // Limit to 4 concurrent generations

        this.requests = new ConcurrentHashMap<>();

        this.memory_cache = new MemoryTileCache(500);
        this.sqlite_cache = new SQLiteTileCache(plugin);
        this.sqlite_cache.init();

        VoxelAtlas.LOGGER.atInfo().log("[VoxelAtlas] Image encoder initialized:");
        VoxelAtlas.LOGGER.atInfo().log("[VoxelAtlas]   - PNG: Available");
        VoxelAtlas.LOGGER.atInfo().log("[VoxelAtlas]   - WebP: "
                + (ImageEncoder.isFormatAvailable(Format.WEBP) ? "Available" : "Not available"));
        VoxelAtlas.LOGGER.atInfo().log("[VoxelAtlas]   - Cache: Memory (500) + SQLite");
    }

    /**
     * Fetch a tile with specified format
     * 
     * @param world  World name
     * @param zoom   Zoom level
     * @param tileX  Tile X coordinate
     * @param tileZ  Tile Z coordinate
     * @param format Image format (PNG or WebP)
     * @return CompletableFuture with encoded tile data
     */
    public CompletableFuture<byte[]> fetch_tile(String world, int zoom, int tileX, int tileZ, Format format) {
        return this.memory_cache.get(world, zoom, tileX, tileZ, format).thenCompose(data -> {
            if (data != null && data.length > 500) {
                return CompletableFuture.completedFuture(data);
            }
            return this.sqlite_cache.get(world, zoom, tileX, tileZ, format).thenCompose(sqliteData -> {
                if (sqliteData != null && sqliteData.length > 500) {
                    this.memory_cache.put(world, zoom, tileX, tileZ, format, sqliteData);
                    return CompletableFuture.completedFuture(sqliteData);
                }

                final String key = this.create_key(world, zoom, tileX, tileZ, format);

                final CompletableFuture<byte[]> pending = this.requests.get(key);
                if (pending != null) {
                    return pending;
                }

                final CompletableFuture<byte[]> future = this.generate_tile(world, zoom, tileX, tileZ, format);
                this.requests.put(key, future);
                future.whenComplete((res, ex) -> {
                    this.requests.remove(key);
                    if (res != null && ex == null) {
                        this.memory_cache.put(world, zoom, tileX, tileZ, format, res);
                        this.sqlite_cache.put(world, zoom, tileX, tileZ, format, res);

                        // Notify clients of update
                        this.broadcast_tile_update(world, tileX, tileZ, zoom);
                    }
                });
                return future;
            });
        });
    }

    /**
     * Generate a tile with specified format
     * 
     * @param world_name World name
     * @param zoom       Zoom level
     * @param tileX      Tile X coordinate
     * @param tileZ      Tile Z coordinate
     * @param format     Image format (PNG or WebP)
     * @return CompletableFuture with encoded tile data
     */
    private CompletableFuture<byte[]> generate_tile(String world_name, int zoom, int tileX, int tileZ, Format format) {
        final World world = Universe.get().getWorld(world_name);
        if (world == null) {
            return CompletableFuture.completedFuture(null);
        }

        // Check if the chunk is generated before attempting to render it
        final boolean display_unexplored = this.plugin.config().get().display_unexplored();
        boolean force_render = false;

        if (!display_unexplored) {
            final Vector3d spawn = WorldDataProvider.get_spawn(world);
            final int spawnChunkX = ((int) spawn.x) >> 5;
            final int spawnChunkZ = ((int) spawn.z) >> 5;
            final int spawnRadius = this.plugin.config().get().spawn_radius();

            // Check if tile is within spawn radius (using simple box check for speed)
            if (Math.abs(tileX - spawnChunkX) <= spawnRadius && Math.abs(tileZ - spawnChunkZ) <= spawnRadius) {
                force_render = true;
            }
        }

        if (!display_unexplored && !force_render && this.is_unexplored(world, tileX, tileZ)) {
            return CompletableFuture.completedFuture(null);
        }

        final WorldMapManager map_manager = world.getWorldMapManager();

        return CompletableFuture.supplyAsync(() -> {
            return map_manager.getImageAsync(tileX, tileZ).join();
        }, this.generation_executor).thenApply(image -> {
            if (image == null)
                return null;

            return ImageEncoder.encode(image, 256, format);
        }).exceptionally(ex -> {
            VoxelAtlas.LOGGER.atSevere().log("[VoxelAtlas] - Failed to generate tile: " + ex.getMessage());
            return null;
        });
    }

    /**
     * Pregenerate tiles for a region with specified format
     * 
     * @param worldName World name
     * @param centerX   Center X coordinate
     * @param centerZ   Center Z coordinate
     * @param radius    Radius in tiles
     * @param format    Image format (PNG or WebP)
     * @return CompletableFuture with number of generated tiles
     */
    public CompletableFuture<Integer> pregenerate(String worldName, int centerX, int centerZ, int radius,
            Format format) {
        final World world = Universe.get().getWorld(worldName);
        if (world == null)
            return CompletableFuture.completedFuture(0);

        return CompletableFuture.supplyAsync(() -> {
            int count = 0;
            for (int x = centerX - radius; x <= centerX + radius; ++x) {
                for (int z = centerZ - radius; z <= centerZ + radius; ++z) {
                    try {
                        byte[] tile = this.fetch_tile(worldName, 0, x, z, format).join();
                        if (tile != null && tile.length > 500) {
                            ++count;
                        }

                        Thread.sleep(10L);
                    } catch (Exception exception) {
                        VoxelAtlas.LOGGER.atSevere()
                                .log("[VoxelAtlas] - Failed to pregenerate tile (" + x + ", " + z + "): "
                                        + exception.getMessage());
                    }
                }
            }
            return count;
        });
    }

    /**
     * Create a cache key for a tile with specified format
     * 
     * @param world  World name
     * @param zoom   Zoom level
     * @param x      Tile X coordinate
     * @param z      Tile Z coordinate
     * @param format Image format
     * @return Cache key string
     */
    public String create_key(String world, int zoom, int x, int z, Format format) {
        return world + "/" + zoom + "/" + x + "/" + z + "/" + format.name().toLowerCase();
    }

    /**
     * Check if a chunk is unexplored (not generated)
     *
     * @param world  World
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @return true if the chunk is unexplored (not generated)
     */
    public boolean is_unexplored(World world, int chunkX, int chunkZ) {
        if (world == null)
            return true;

        final ChunkStore chunk_store = world.getChunkStore();

        // Check storage (on disk)
        try {
            final Store<ChunkStore> store = chunk_store.getStore();

            // This relies on the world using IndexedStorage
            if (chunk_store.getLoader() instanceof IndexedStorageChunkLoader) {
                final var cache = store.getResource(IndexedStorageCache.getResourceType());

                int regionX = chunkX >> 5;
                int regionZ = chunkZ >> 5;

                // Checks if the region file exists
                final IndexedStorageFile region_file = cache.getOrTryOpen(regionX, regionZ);
                if (region_file != null) {
                    int localX = chunkX & 0x1F;
                    int localZ = chunkZ & 0x1F;
                    int index = ChunkUtil.indexColumn(localX, localZ);

                    // Check if the chunk index exists in the region file keys
                    if (region_file.keys().contains(index)) {
                        return false;
                    }
                }
            }
        } catch (Exception e) {
            VoxelAtlas.LOGGER.atSevere().log("[VoxelAtlas] Error checking chunk generation: " + e.getMessage());
        }

        return true;
    }

    public void purge_expired(long max_age_ms) {
        if (this.sqlite_cache != null) {
            this.sqlite_cache.purge_expired(max_age_ms);
        }
    }

    public void close() {
        if (this.sqlite_cache != null) {
            this.sqlite_cache.close();
        }
        if (this.memory_cache != null) {
            this.memory_cache.close();
        }
        if (this.generation_executor != null) {
            this.generation_executor.shutdown();
        }
    }

    /**
     * Update tiles around a center point using a spiral pattern
     * 
     * @param worldName World name
     * @param centerX   Center X coordinate
     * @param centerZ   Center Z coordinate
     * @param radius    Radius in tiles
     */
    public void update_tiles_around(String worldName, int centerX, int centerZ, int radius) {
        final World world = Universe.get().getWorld(worldName);
        if (world == null)
            return;

        // Simple spiral iterator implementation
        int x = 0;
        int z = 0;
        int dx = 0;
        int dz = -1;

        // Max steps for a square of side 2*radius + 1
        int max_steps = (2 * radius + 1) * (2 * radius + 1);

        for (int i = 0; i < max_steps; i++) {
            if ((-radius <= x) && (x <= radius) && (-radius <= z) && (z <= radius)) {
                final int tileX = centerX + x;
                final int tileZ = centerZ + z;

                this.fetch_tile(worldName, 0, tileX, tileZ, Format.PNG);
            }

            if ((x == z) || ((x < 0) && (x == -z)) || ((x > 0) && (x == 1 - z))) {
                int t = dx;
                dx = -dz;
                dz = t;
            }
            x += dx;
            z += dz;
        }
    }

    public void broadcast_tile_update(String worldName, int tileX, int tileZ, int zoom) {
        if (this.plugin.websocket() == null || this.plugin.websocket().connections_count() == 0) {
            return;
        }

        final com.google.gson.JsonObject message = new com.google.gson.JsonObject();
        message.addProperty("type", "tile_update");
        message.addProperty("world", worldName);
        message.addProperty("x", tileX);
        message.addProperty("z", tileZ);
        message.addProperty("zoom", zoom);

        this.plugin.websocket().broadcast(message);
    }
}
