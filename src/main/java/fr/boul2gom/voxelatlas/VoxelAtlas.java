package fr.boul2gom.voxelatlas;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.Config;
import fr.boul2gom.voxelatlas.dynmap.PlayerTracker;
import fr.boul2gom.voxelatlas.dynmap.TileManager;
import fr.boul2gom.voxelatlas.dynmap.data.WorldDataProvider;
import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder;
import fr.boul2gom.voxelatlas.netty.NettyServer;
import fr.boul2gom.voxelatlas.utils.Configuration;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class VoxelAtlas extends JavaPlugin {

    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final Config<Configuration> config;

    private TileManager tiles;
    private PlayerTracker tracker;

    private NettyServer netty;

    public VoxelAtlas(@Nonnull JavaPluginInit init) {
        super(init);

        this.config = this.withConfig("VoxelAtlas", Configuration.CODEC);
    }

    @Override
    public void setup() {
        this.config.load();
        this.config.save();

        this.tiles = new TileManager(this);
        this.tracker = new PlayerTracker(this);

        this.netty = new NettyServer(this, this.config.get().webserver_port());
    }

    @Override
    public void start() {
        this.netty.start();
        this.tracker.start();

        // Purge expired tiles
        final int expiration = this.config.get().cache_expiration_hours();
        if (expiration > 0) {
            final long max_age_ms = expiration * 3600 * 1000L;
            this.tiles.purge_expired(max_age_ms);
        }

        // Pregeneration (only on first start)
        if (!this.config.get().pregenerate()) return;

        final Path pregen_lock = this.data_directory().resolve("pregen_done");
        if (Files.exists(pregen_lock)) {
            return;
        }

        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        scheduler.schedule(() -> {
            try {
                Files.createFile(pregen_lock);
            } catch (Exception e) {
                VoxelAtlas.LOGGER.atWarning().log("[VoxelAtlas] Failed to create pregen lock file: " + e.getMessage());
            }

            VoxelAtlas.LOGGER.atInfo().log("[VoxelAtlas] First start detected: Pregenerating tiles...");
            final long start = System.currentTimeMillis();
            final int radius = this.config.get().pregen_radius();

            for (final World world : Universe.get().getWorlds().values()) {
                final Vector3d world_spawn = WorldDataProvider.get_spawn(world);
                final int chunkX = ChunkUtil.chunkCoordinate((int) world_spawn.x);
                final int chunkZ = ChunkUtil.chunkCoordinate((int) world_spawn.z);

                this.tiles.pregenerate(world.getName(), chunkX, chunkZ, radius, ImageEncoder.Format.PNG).thenRun(() -> {
                    final long duration = System.currentTimeMillis() - start;
                    VoxelAtlas.LOGGER.atInfo().log("[VoxelAtlas] Pregeneration complete for world " + world.getName()
                            + " in " + duration + " ms");
                });
            }
        }, 5, TimeUnit.SECONDS);
    }

    @Override
    public void shutdown() {
        if (this.tiles != null) {
            this.tiles.close();
        }
        if (this.tracker != null) {
            this.tracker.shutdown();
        }
        if (this.netty != null) {
            this.netty.shutdown();
        }
    }

    public Config<Configuration> config() {
        return this.config;
    }

    public TileManager tiles() {
        return this.tiles;
    }

    public PlayerTracker tracker() {
        return this.tracker;
    }

    public NettyServer netty() {
        return this.netty;
    }

    public Path data_directory() {
        return this.getDataDirectory();
    }
}
