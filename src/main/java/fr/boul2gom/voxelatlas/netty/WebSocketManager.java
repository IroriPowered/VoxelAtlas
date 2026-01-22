package fr.boul2gom.voxelatlas.netty;

import com.google.gson.JsonObject;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * Manages active WebSocket connections.
 * <p>
 * This class provides methods to add/remove channels and broadcast messages to
 * all connected clients.
 * It uses a Netty {@link ChannelGroup} to manage the connections efficiently.
 * </p>
 */
public class WebSocketManager {

    /** Group of all active WebSocket channels */
    private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    /**
     * Adds a channel to the managed group.
     *
     * @param channel The channel to add.
     */
    public void add_channel(Channel channel) {
        this.channels.add(channel);
    }

    /**
     * Removes a channel from the managed group.
     *
     * @param channel The channel to remove.
     */
    public void remove_channel(Channel channel) {
        this.channels.remove(channel);
    }

    /**
     * Broadcasts a JSON message to all connected clients.
     *
     * @param message The JSON object to broadcast.
     */
    public void broadcast(JsonObject message) {
        if (this.channels.isEmpty()) {
            return;
        }

        final TextWebSocketFrame frame = new TextWebSocketFrame(NettyServer.GSON.toJson(message));
        this.channels.writeAndFlush(frame);
    }

    /**
     * Shuts down the manager and closes all connections.
     */
    public void shutdown() {
        this.channels.close().awaitUninterruptibly();
    }

    /**
     * Gets the number of active connections.
     *
     * @return The count of active channels.
     */
    public int connections_count() {
        return this.channels.size();
    }
}
