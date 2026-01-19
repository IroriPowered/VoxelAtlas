package fr.boul2gom.voxelatlas.netty;

import fr.boul2gom.voxelatlas.VoxelAtlas;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * WebSocket handler for real-time player tracking
 */
public class WebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final VoxelAtlas plugin;
    private final ChannelGroup channels;

    public WebSocketHandler(VoxelAtlas plugin) {
        this.plugin = plugin;
        this.channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.channels.add(ctx.channel());
        this.plugin.websocket().add_channel(ctx.channel());

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        this.channels.remove(ctx.channel());
        this.plugin.websocket().remove_channel(ctx.channel());

        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        // Handle incoming WebSocket frames
        if (frame instanceof TextWebSocketFrame text_frame) {
            final String message = text_frame.text();

            VoxelAtlas.LOGGER.atInfo().log("[VoxelAtlas] Received WebSocket message: " + message);
            // Handle different message types if needed
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        VoxelAtlas.LOGGER.atSevere().log("[VoxelAtlas] WebSocket error: " + cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }

    /**
     * Get the channel group for broadcasting
     * 
     * @return ChannelGroup with all active WebSocket connections
     */
    public ChannelGroup channels() {
        return this.channels;
    }

    /**
     * Get the number of active connections
     * 
     * @return Number of active WebSocket connections
     */
    public int connections_count() {
        return this.channels.size();
    }
}
