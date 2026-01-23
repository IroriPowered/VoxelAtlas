package fr.boul2gom.voxelatlas;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.UUID;
import fr.boul2gom.voxelatlas.dynmap.PlayerTracker;
import fr.boul2gom.voxelatlas.dynmap.TileManager;
import fr.boul2gom.voxelatlas.dynmap.data.WorldDataProvider;
import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder;
import fr.boul2gom.voxelatlas.netty.NettyServer;
import fr.boul2gom.voxelatlas.netty.WebSocketManager;
import fr.boul2gom.voxelatlas.utils.Configuration;
import fr.boul2gom.voxelatlas.utils.UpdateChecker;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main plugin class for VoxelAtlas.
 * <p>
 * This class serves as the entry point for the plugin. It handles
 * initialization,
 * configuration loading, component instantiation (Netty server, TileManager,
 * PlayerTracker),
 * and resource cleanup on shutdown.
 * </p>
 */
public class VoxelAtlas extends JavaPlugin {

    /** The plugin-wide logger instance */
    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** The configuration wrapper */
    private final Config<Configuration> config;

    /** The core tile management component */
    private TileManager tiles;
    /** The player tracking component */
    private PlayerTracker tracker;

    /** The Netty HTTP server */
    private NettyServer netty;
    /** The WebSocket communication manager */
    private WebSocketManager websocket;

    /**
     * Called during plugin initialization by the server.
     *
     * @param init The initialization context.
     */
    public VoxelAtlas(@Nonnull JavaPluginInit init) {
        super(init);

        this.config = this.withConfig("VoxelAtlas", Configuration.CODEC);
    }

    /**
     * Sets up the plugin components.
     * <p>
     * Initializes configuration, block colors, and core subsystems (WebSocket,
     * TileManager, Netty).
     * </p>
     */
    @Override
    public void setup() {
        LOGGER.atInfo().log("[VoxelAtlas] Initializing...");
        this.config.load();
        this.config.save();

        new UpdateChecker(this).check();

        this.websocket = new WebSocketManager();
        this.tiles = new TileManager(this);
        this.tracker = new PlayerTracker(this);

        this.netty = new NettyServer(this, this.config.get().webserver_port());

        final CommandRegistry commands = this.getCommandRegistry();
        commands.registerCommand(new VoxelCommand(this));
    }

    /**
     * Starts the plugin.
     * <p>
     * Starts the Netty server, player tracker, and schedules periodic tasks
     * for tile expiration and view radius updates.
     * Also handles initial pre-generation if configured.
     * </p>
     */
    @Override
    public void start() {
        LOGGER.atInfo().log("[VoxelAtlas] Starting...");
        this.netty.start();
        this.tracker.start();

        // Purge expired tiles
        final int expiration = this.config.get().cache_expiration_hours();
        if (expiration > 0) {
            final long max_age_ms = expiration * 3600 * 1000L;
            this.tiles.purge_expired(max_age_ms);
        }

        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        // Schedule View Radius updates
        final int view_radius = this.config.get().view_radius();
        final int view_period = this.config.get().view_update_period();
        if (view_radius > 0 && view_period > 0) {
            scheduler.scheduleAtFixedRate(() -> {
                for (final PlayerRef player : Universe.get().getPlayers()) {
                    final UUID worldUuid = player.getWorldUuid();
                    if (worldUuid == null)
                        continue;

                    final World world = Universe.get().getWorld(worldUuid);
                    if (world == null)
                        continue;

                    final Vector3d pos = player.getTransform().getPosition();
                    final int chunkX = ChunkUtil.chunkCoordinate((int) pos.x);
                    final int chunkZ = ChunkUtil.chunkCoordinate((int) pos.z);

                    this.tiles.update_tiles_around(world.getName(), chunkX, chunkZ, view_radius);
                }
            }, view_period, view_period, TimeUnit.SECONDS);
        }

        // Pregeneration (only on first start)
        if (!this.config.get().pregenerate())
            return;

        final Path pregen_lock = this.data_directory().resolve("pregen_done");
        if (Files.exists(pregen_lock)) {
            return;
        }

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

    /**
     * Shuts down the plugin.
     * <p>
     * Closes all subsystems (tiles, tracker, Netty, WebSocket) gracefully.
     * </p>
     */
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
        if (this.websocket != null) {
            this.websocket.shutdown();
        }
    }

    /**
     * Gets the plugin configuration.
     *
     * @return The configuration wrapper.
     */
    public Config<Configuration> config() {
        return this.config;
    }

    /**
     * Gets the tile manager.
     *
     * @return The {@link TileManager}.
     */
    public TileManager tiles() {
        return this.tiles;
    }

    /**
     * Gets the player tracker.
     *
     * @return The {@link PlayerTracker}.
     */
    public PlayerTracker tracker() {
        return this.tracker;
    }

    /**
     * Gets the Netty server instance.
     *
     * @return The {@link NettyServer}.
     */
    public NettyServer netty() {
        return this.netty;
    }

    /**
     * Gets the plugin data directory path.
     *
     * @return The {@link Path} to the data directory.
     */
    public Path data_directory() {
        return this.getDataDirectory();
    }

    /**
     * Gets the WebSocket manager.
     *
     * @return The {@link WebSocketManager}.
     */
    public WebSocketManager websocket() {
        return this.websocket;
    }
}
