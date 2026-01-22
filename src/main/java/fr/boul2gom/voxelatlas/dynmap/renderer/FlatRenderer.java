package fr.boul2gom.voxelatlas.dynmap.renderer;

import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import fr.boul2gom.voxelatlas.VoxelAtlas;
import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder;
import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder.Format;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementation of {@link Renderer} for flat 2D map generation.
 * <p>
 * This renderer generates top-down 2D views of the world.
 * It fetches map images from Hytale's WorldMapManager and encodes them.
 * </p>
 */
public class FlatRenderer implements Renderer {

    /*
     * The executor service for asynchronous rendering tasks, separate from the IO
     * pool
     */
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    /**
     * Renders a flat 2D tile for the given coordinates and zoom level.
     *
     * @param worldName The name of the world to render from. Cannot be null.
     * @param x         The X coordinate of the tile.
     * @param z         The Z coordinate of the tile.
     * @param zoom      The zoom level of the tile.
     * @param format    The desired image format. Cannot be null.
     * @return A {@link CompletableFuture} containing the encoded image data,
     *         or completing with null if the world/image is unavailable or an error
     *         occurs.
     */
    @Override
    public CompletableFuture<byte[]> render(String worldName, int x, int z, int zoom, Format format) {
        final World world = Universe.get().getWorld(worldName);
        if (world == null) return CompletableFuture.completedFuture(null);

        final WorldMapManager mapManager = world.getWorldMapManager();

        return CompletableFuture.supplyAsync(() -> {
            try {
                final MapImage image = mapManager.getImageAsync(x, z).join();
                if (image == null)
                    return null;

                return ImageEncoder.encode(image, 256, format);
            } catch (Exception e) {
                VoxelAtlas.LOGGER.atWarning().log("FlatRenderer error: " + e.getMessage());
                return null;
            }
        }, executor);
    }
}
