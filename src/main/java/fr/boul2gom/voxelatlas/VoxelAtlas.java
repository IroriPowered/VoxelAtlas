package fr.boul2gom.voxelatlas;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import fr.boul2gom.voxelatlas.dynmap.PlayerTracker;
import fr.boul2gom.voxelatlas.dynmap.TileManager;
import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder;
import fr.boul2gom.voxelatlas.netty.NettyServer;
import fr.boul2gom.voxelatlas.utils.Configuration;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class VoxelAtlas extends JavaPlugin {

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
        this.tiles = new TileManager(this);
        this.tracker = new PlayerTracker(this);

        this.netty = new NettyServer(this, this.config.get().webserver_port());
    }

    @Override
    public void start() {
        this.netty.start();
        this.tracker.start();

        if (!this.config.get().pregenerate()) return;
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        scheduler.schedule(() -> {
            System.out.println("[VoxelAtlas] Pregenerating tiles...");
            final long start = System.currentTimeMillis();
            final int radius = this.config.get().pregen_radius();

            this.tiles.pregenerate("default", 0, 0, radius, ImageEncoder.Format.PNG).thenRun(() -> {
                final long duration = System.currentTimeMillis() - start;
                System.out.println("[VoxelAtlas] Pregeneration complete in " + duration + " ms");
            });
        }, 5, java.util.concurrent.TimeUnit.SECONDS);
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

    public HytaleLogger logger() {
        return this.getLogger();
    }

    public Path data_directory() {
        return this.getDataDirectory();
    }
}
