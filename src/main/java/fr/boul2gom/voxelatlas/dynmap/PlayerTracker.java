package fr.boul2gom.voxelatlas.dynmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import fr.boul2gom.voxelatlas.VoxelAtlas;
import fr.boul2gom.voxelatlas.dynmap.data.WorldDataProvider;

import java.util.concurrent.*;

/**
 * Component responsible for tracking and broadcasting player positions.
 * <p>
 * This class periodically collects player data from the server universe and
 * broadcasts
 * it to connected WebSocket clients to allow live tracking on the map.
 * </p>
 */
public class PlayerTracker {

    /** The main plugin instance */
    private final VoxelAtlas plugin;
    /** The scheduled executor service for the tracking task */
    private ScheduledExecutorService pool;

    /**
     * Create a new player tracker.
     *
     * @param plugin The VoxelAtlas plugin instance.
     */
    public PlayerTracker(VoxelAtlas plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the player tracking task.
     * <p>
     * Initializes a scheduled thread pool and submits a periodic task that runs
     * every 5 seconds.
     * </p>
     */
    public void start() {
        final ThreadFactory factory = (runnable -> {
            Thread thread = new Thread(runnable, "VoxelAtlas - Tracker");
            thread.setDaemon(true);
            return thread;
        });

        this.pool = Executors.newScheduledThreadPool(2, factory);
        this.pool.scheduleAtFixedRate(this::broadcast_positions, 5, 5, TimeUnit.SECONDS);

        VoxelAtlas.LOGGER.atInfo().log("[VoxelAtlas] Player tracker started - Broadcasting every 5 seconds");
    }

    /**
     * Shuts down the player tracking task and releases resources.
     * <p>
     * Stops the scheduled executor and waits for the current task to finish.
     * </p>
     */
    public void shutdown() {
        if (this.pool != null) {
            this.pool.shutdown();
            try {
                if (!this.pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    this.pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                this.pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        VoxelAtlas.LOGGER.atInfo().log("[VoxelAtlas] Player tracker shutdown complete");
    }

    /**
     * Collects and broadcasts the positions of all players.
     * <p>
     * Iterates through all available worlds, gathers player data, and sends a JSON
     * payload
     * via the WebSocket manager. Does nothing if no clients are connected.
     * </p>
     */
    private void broadcast_positions() {
        if (this.plugin.websocket().connections_count() == 0) {
            return;
        }

        final JsonArray data = new JsonArray();
        for (final World world : Universe.get().getWorlds().values()) {
            final JsonArray world_players = WorldDataProvider.available_players(world);

            final JsonObject world_data = new JsonObject();
            world_data.addProperty("world", world.getName());
            world_data.add("players", world_players);

            data.add(world_data);
        }

        final JsonObject message = new JsonObject();
        message.addProperty("type", "player_positions");
        message.add("data", data);

        this.plugin.websocket().broadcast(message);
    }
}
