package fr.boul2gom.voxelatlas.netty;

import fr.boul2gom.voxelatlas.VoxelAtlas;
import fr.boul2gom.voxelatlas.netty.network.HttpContext;
import fr.boul2gom.voxelatlas.netty.network.request.HttpRequest;
import fr.boul2gom.voxelatlas.netty.network.request.HttpRequestParameter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Netty channel handler for processing incoming HTTP requests.
 * <p>
 * This handler converts Netty {@link FullHttpRequest} objects into internal
 * {@link HttpRequest} wrappers
 * and dispatches them to the plugin's main
 * {@link fr.boul2gom.voxelatlas.netty.router.HttpRouter} (via NettyServer).
 * </p>
 */
public class HttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    /** The main plugin instance */
    private final VoxelAtlas plugin;

    /**
     * Create a new HTTP server handler.
     *
     * @param plugin The VoxelAtlas plugin instance.
     */
    public HttpServerHandler(VoxelAtlas plugin) {
        this.plugin = plugin;
    }

    /**
     * Reads and processes an incoming HTTP request.
     *
     * @param ctx The channel handler context.
     * @param msg The full HTTP request message.
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
        final HttpContext context = new HttpContext(ctx);

        if (!msg.decoderResult().isSuccess()) {
            context.error("Bad Request", HttpResponseStatus.BAD_REQUEST);
            return;
        }

        try {
            this.plugin.netty().dispatch(new HttpRequest(msg, context, this.query_parameters(msg.uri())), context);
        } catch (Exception e) {
            context.error("Internal Server Error", HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Parses query parameters from the request URI.
     *
     * @param uri The request URI.
     * @return A list of parsed {@link HttpRequestParameter}s.
     */
    private List<HttpRequestParameter> query_parameters(String uri) {
        final QueryStringDecoder decoder = new QueryStringDecoder(uri, StandardCharsets.UTF_8);
        final Map<String, List<String>> uri_params = decoder.parameters();
        final List<HttpRequestParameter> query_params = new ArrayList<>();

        for (Map.Entry<String, List<String>> parameter : uri_params.entrySet()) {
            query_params.add(new HttpRequestParameter(parameter.getKey(), parameter.getValue()));
        }

        return query_params;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!(cause instanceof SocketException)) {
            cause.printStackTrace();
        }

        ctx.close();
    }

    /**
     * Pipeline initializer for the HTTP server.
     * <p>
     * Sets up the Netty pipeline with codecs, aggregators, and handlers.
     * </p>
     */
    public static class Initializer extends ChannelInitializer<SocketChannel> {

        private final VoxelAtlas plugin;

        public Initializer(VoxelAtlas plugin) {
            this.plugin = plugin;
        }

        @Override
        protected void initChannel(SocketChannel ch) {
            ch.pipeline()
                    .addLast("codec", new HttpServerCodec())
                    .addLast("aggregator", new HttpObjectAggregator(Integer.MAX_VALUE))
                    .addLast("ws-protocol", new WebSocketServerProtocolHandler("/ws/players", null, true))
                    .addLast("ws-handler", new WebSocketHandler(this.plugin))
                    .addLast("http-handler", new HttpServerHandler(this.plugin));
        }
    }
}
