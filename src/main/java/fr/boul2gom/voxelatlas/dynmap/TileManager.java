package fr.boul2gom.voxelatlas.dynmap;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import fr.boul2gom.voxelatlas.VoxelAtlas;
import fr.boul2gom.voxelatlas.dynmap.cache.provider.MemoryTileCache;
import fr.boul2gom.voxelatlas.dynmap.cache.provider.SQLiteTileCache;
import fr.boul2gom.voxelatlas.dynmap.cache.TileCache;
import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder;
import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder.Format;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class TileManager {

    private final VoxelAtlas plugin;

    private final ConcurrentHashMap<String, CompletableFuture<byte[]>> requests;
    private final TileCache memory_cache;
    private final TileCache sqlite_cache;

    public TileManager(VoxelAtlas plugin) {
        this.plugin = plugin;

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
            if (data != null) {
                return CompletableFuture.completedFuture(data);
            }
            return this.sqlite_cache.get(world, zoom, tileX, tileZ, format).thenCompose(sqliteData -> {
                if (sqliteData != null) {
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
            return CompletableFuture.completedFuture(ImageEncoder.empty(256, format));
        }

        // Check if the chunk is generated before attempting to render it
        //if (!this.plugin.config().get().display_unexplored() && !this.is_chunk_generated(world, tileX, tileZ)) {
        //    VoxelAtlas.LOGGER.atInfo().log("[VoxelAtlas] - Chunk not generated because unexplored: " + tileX + ", " + tileZ);
        //    return CompletableFuture.completedFuture(ImageEncoder.empty(256, format));
        //}

        final WorldMapManager map_manager = world.getWorldMapManager();

        return map_manager.getImageAsync(tileX, tileZ).thenApply(image -> {
            if (image == null) return ImageEncoder.empty(256, format);

            return ImageEncoder.encode(image, 256, format);
        }).exceptionally(ex -> {
            VoxelAtlas.LOGGER.atSevere().log("[VoxelAtlas] - Failed to generate tile: " + ex.getMessage());
            return ImageEncoder.empty(256, format);
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
    public CompletableFuture<Integer> pregenerate(String worldName, int centerX, int centerZ, int radius, Format format) {
        final World world = Universe.get().getWorld(worldName);
        if (world == null) return CompletableFuture.completedFuture(0);

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
                        VoxelAtlas.LOGGER.atSevere().log("[VoxelAtlas] - Failed to pregenerate tile (" + x + ", " + z + "): "
                                + exception.getMessage());
                    }
                }
            }
            return count;
        });
    }

    public Vector3d world_spawn(World world) {
        final ISpawnProvider provider = world.getWorldConfig().getSpawnProvider();

        if (provider != null) {
            final Transform global = provider.getSpawnPoint(world, world.getWorldConfig().getUuid());
            if (global != null) return global.getPosition();
        }

        return new Vector3d(0, 0, 0);
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

    public void close() {
        if (this.sqlite_cache != null) {
            this.sqlite_cache.close();
        }
        if (this.memory_cache != null) {
            this.memory_cache.close();
        }
    }
}
