package fr.boul2gom.voxelaltas.netty;

import fr.boul2gom.voxelaltas.VoxelAtlas;
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
        this.plugin.tracker().add_channel(ctx.channel());

        System.out.println("[VoxelAtlas] WebSocket client connected: " + ctx.channel().remoteAddress());
        System.out.println("[VoxelAtlas] Active WebSocket connections: " + this.channels.size());

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        this.channels.remove(ctx.channel());
        this.plugin.tracker().remove_channel(ctx.channel());

        System.out.println("[VoxelAtlas] WebSocket client disconnected: " + ctx.channel().remoteAddress());
        System.out.println("[VoxelAtlas] Active WebSocket connections: " + this.channels.size());

        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        // Handle incoming WebSocket frames
        if (frame instanceof TextWebSocketFrame text_frame) {
            final String message = text_frame.text();

            System.out.println("[VoxelAtlas] Received WebSocket message: " + message);
            //Handle different message types if needed
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[VoxelAtlas] WebSocket error: " + cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }

    /**
     * Get the channel group for broadcasting
     * @return ChannelGroup with all active WebSocket connections
     */
    public ChannelGroup channels() {
        return this.channels;
    }

    /**
     * Get the number of active connections
     * @return Number of active WebSocket connections
     */
    public int connections_count() {
        return this.channels.size();
    }
}
