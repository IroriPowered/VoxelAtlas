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
 * WebSocket handler for real-time player tracking.
 * <p>
 * This handler manages WebSocket connections for the "/ws/players" endpoint.
 * It tracks active connections in a {@link ChannelGroup} and registers them
 * with the {@link WebSocketManager}.
 * </p>
 */
public class WebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    /** The main plugin instance */
    private final VoxelAtlas plugin;
    /** The group of active channels handled by this handler */
    private final ChannelGroup channels;

    /**
     * Create a new WebSocket handler.
     *
     * @param plugin The VoxelAtlas plugin instance.
     */
    public WebSocketHandler(VoxelAtlas plugin) {
        this.plugin = plugin;
        this.channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    }

    /**
     * Invoked when a channel becomes active (connected).
     *
     * @param ctx The channel handler context.
     * @throws Exception If an error occurs.
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.channels.add(ctx.channel());
        this.plugin.websocket().add_channel(ctx.channel());

        super.channelActive(ctx);
    }

    /**
     * Invoked when a channel becomes inactive (disconnected).
     *
     * @param ctx The channel handler context.
     * @throws Exception If an error occurs.
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        this.channels.remove(ctx.channel());
        this.plugin.websocket().remove_channel(ctx.channel());

        super.channelInactive(ctx);
    }

    /**
     * processes incoming WebSocket frames.
     *
     * @param ctx   The channel handler context.
     * @param frame The incoming WebSocket frame.
     * @throws Exception If an error occurs during processing.
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        // Handle incoming WebSocket frames
        if (frame instanceof TextWebSocketFrame text_frame) {
            final String message = text_frame.text();

            VoxelAtlas.LOGGER.atInfo().log("[VoxelAtlas] Received WebSocket message: " + message);
            // Handle different message types if needed
        }
    }

    /**
     * Handles exceptions caught in the pipeline.
     *
     * @param ctx   The channel handler context.
     * @param cause The exception caught.
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        VoxelAtlas.LOGGER.atSevere().log("[VoxelAtlas] WebSocket error: " + cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }

    /**
     * Gets the channel group managed by this handler.
     *
     * @return The channel group containing active WebSocket connections.
     */
    public ChannelGroup channels() {
        return this.channels;
    }

    /**
     * Gets the number of active connections managed by this handler.
     *
     * @return The count of active connections.
     */
    public int connections_count() {
        return this.channels.size();
    }
}
