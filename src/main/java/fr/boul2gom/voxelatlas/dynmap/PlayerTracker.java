package fr.boul2gom.voxelatlas.dynmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import fr.boul2gom.voxelatlas.VoxelAtlas;
import fr.boul2gom.voxelatlas.dynmap.data.WorldDataProvider;
import fr.boul2gom.voxelatlas.netty.NettyServer;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.util.concurrent.*;

public class PlayerTracker {

    private final ChannelGroup channels;
    private ScheduledExecutorService pool;

    public PlayerTracker(VoxelAtlas plugin) {
        this.channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    }

    public void start() {
        final ThreadFactory factory = (runnable -> {
            Thread thread = new Thread(runnable, "VoxelAtlas - Tracker");
            thread.setDaemon(true);
            return thread;
        });

        this.pool = Executors.newScheduledThreadPool(2, factory);
        this.pool.scheduleAtFixedRate(this::broadcast_positions, 5, 5, TimeUnit.SECONDS);

        System.out.println("[VoxelAtlas] Player tracker started - Broadcasting every 5 seconds");
    }

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

        this.channels.close().awaitUninterruptibly();
        System.out.println("[VoxelAtlas] Player tracker shutdown complete");
    }

    public void add_channel(Channel channel) {
        this.channels.add(channel);
    }

    public void remove_channel(Channel channel) {
        this.channels.remove(channel);
    }

    public int connections_count() {
        return this.channels.size();
    }

    private void broadcast_positions() {
        if (this.channels.isEmpty()) {
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

        final TextWebSocketFrame frame = new TextWebSocketFrame(NettyServer.GSON.toJson(message));
        this.channels.writeAndFlush(frame, Channel::isActive).awaitUninterruptibly();
    }

    public ChannelGroup channels() {
        return this.channels;
    }
}
