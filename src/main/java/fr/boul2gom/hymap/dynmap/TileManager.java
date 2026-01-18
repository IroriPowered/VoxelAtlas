package fr.boul2gom.hymap.dynmap;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import fr.boul2gom.hymap.HytaleMap;
import fr.boul2gom.hymap.dynmap.encoder.ImageEncoder;
import fr.boul2gom.hymap.dynmap.encoder.ImageEncoder.Format;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class TileManager {

    private final HytaleMap plugin;

    private final ConcurrentHashMap<String, CompletableFuture<byte[]>> requests;

    public TileManager(HytaleMap plugin) {
        this.plugin = plugin;

        this.requests = new ConcurrentHashMap<>();

        // Log available formats
        System.out.println("[HytaleMap] Image encoder initialized:");
        System.out.println("[HytaleMap]   - PNG: Available");
        System.out.println("[HytaleMap]   - WebP: " + (ImageEncoder.isFormatAvailable(Format.WEBP) ? "Available" : "Not available"));
    }

    /**
     * Fetch a tile with default format (PNG)
     * @deprecated Use fetch_tile with format parameter
     */
    public CompletableFuture<byte[]> fetch_tile(String world, int zoom, int tileX, int tileZ) {
        return fetch_tile(world, zoom, tileX, tileZ, Format.PNG);
    }

    /**
     * Fetch a tile with specified format
     * @param world World name
     * @param zoom Zoom level
     * @param tileX Tile X coordinate
     * @param tileZ Tile Z coordinate
     * @param format Image format (PNG or WebP)
     * @return CompletableFuture with encoded tile data
     */
    public CompletableFuture<byte[]> fetch_tile(String world, int zoom, int tileX, int tileZ, Format format) {
        final String key = this.create_key(world, zoom, tileX, tileZ, format);

        final CompletableFuture<byte[]> pending = this.requests.get(key);
        if (pending != null) {
            return pending;
        }

        final CompletableFuture<byte[]> future = this.generate_tile(world, zoom, tileX, tileZ, format);
        this.requests.put(key, future);
        future.whenComplete((_, _) -> this.requests.remove(key));
        return future;
    }

    /**
     * Generate a tile with default format (PNG)
     * @deprecated Use generate_tile with format parameter
     */
    private CompletableFuture<byte[]> generate_tile(String world_name, int zoom, int tileX, int tileZ) {
        return generate_tile(world_name, zoom, tileX, tileZ, Format.PNG);
    }

    /**
     * Generate a tile with specified format
     * @param world_name World name
     * @param zoom Zoom level
     * @param tileX Tile X coordinate
     * @param tileZ Tile Z coordinate
     * @param format Image format (PNG or WebP)
     * @return CompletableFuture with encoded tile data
     */
    private CompletableFuture<byte[]> generate_tile(String world_name, int zoom, int tileX, int tileZ, Format format) {
        final World world = Universe.get().getWorld(world_name);
        if (world == null) {
            return CompletableFuture.completedFuture(ImageEncoder.empty(256, format));
        }

        //TODO: Allow to render only explored chunks
        //if (!this.chunk_explored(world, tileX, tileZ)) {
        //    return CompletableFuture.completedFuture(ImageEncoder.empty(256, format));
        //}

        final WorldMapManager map_manager = world.getWorldMapManager();

        return map_manager.getImageAsync(tileX, tileZ).thenApply(image -> {
            if (image == null) return ImageEncoder.empty(256, format);

            return ImageEncoder.encode(image, 256, format);
        }).exceptionally(ex -> {
            System.err.println("[HytaleMap] - Failed to generate tile: " + ex.getMessage());
            return ImageEncoder.empty(256, format);
        });
    }

    /**
     * Pregenerate tiles for a region with default format (PNG)
     * @deprecated Use pregenerate with format parameter
     */
    public CompletableFuture<Integer> pregenerate(String worldName, int centerX, int centerZ, int radius) {
        return pregenerate(worldName, centerX, centerZ, radius, Format.PNG);
    }

    /**
     * Pregenerate tiles for a region with specified format
     * @param worldName World name
     * @param centerX Center X coordinate
     * @param centerZ Center Z coordinate
     * @param radius Radius in tiles
     * @param format Image format (PNG or WebP)
     * @return CompletableFuture with number of generated tiles
     */
    public CompletableFuture<Integer> pregenerate(String worldName, int centerX, int centerZ, int radius, Format format) {
        final World world = Universe.get().getWorld(worldName);
        if (world == null) return CompletableFuture.completedFuture(0);

        return CompletableFuture.supplyAsync(() -> {
            int count = 0;
            for (int x = centerX - radius; x <= centerX + radius; ++x) {
                for (int z = centerZ - radius; z <= centerZ + radius; ++z) {
                    //if (!this.chunk_explored(world, x, z)) continue;
                    try {
                        byte[] tile = this.generate_tile(worldName, 0, x, z, format).join();
                        if (tile != null && tile.length > 100) {
                            ++count;
                        }
                        Thread.sleep(50L);
                    } catch (Exception exception) {
                        System.err.println("[HytaleMap] - Failed to pregenerate tile (" + x + ", " + z + "): " + exception.getMessage());
                    }
                }
            }
            return count;
        });
    }

    /**
     * Create a cache key for a tile with default format
     * @deprecated Use create_key with format parameter
     */
    public String create_key(String world, int zoom, int x, int z) {
        return create_key(world, zoom, x, z, Format.PNG);
    }

    /**
     * Create a cache key for a tile with specified format
     * @param world World name
     * @param zoom Zoom level
     * @param x Tile X coordinate
     * @param z Tile Z coordinate
     * @param format Image format
     * @return Cache key string
     */
    public String create_key(String world, int zoom, int x, int z, Format format) {
        return world + "/" + zoom + "/" + x + "/" + z + "/" + format.name().toLowerCase();
    }
}
