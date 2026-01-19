package fr.boul2gom.voxelatlas.netty;

import com.google.gson.JsonObject;
import fr.boul2gom.voxelatlas.VoxelAtlas;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;

public class WebSocketManager {

    private final VoxelAtlas plugin;
    private final ChannelGroup channels;

    public WebSocketManager(VoxelAtlas plugin) {
        this.plugin = plugin;
        this.channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    }

    public void add_channel(Channel channel) {
        this.channels.add(channel);
    }

    public void remove_channel(Channel channel) {
        this.channels.remove(channel);
    }

    public void broadcast(JsonObject message) {
        if (this.channels.isEmpty()) {
            return;
        }

        final TextWebSocketFrame frame = new TextWebSocketFrame(NettyServer.GSON.toJson(message));
        this.channels.writeAndFlush(frame);
    }

    public void shutdown() {
        this.channels.close().awaitUninterruptibly();
    }

    public int connections_count() {
        return this.channels.size();
    }
}
