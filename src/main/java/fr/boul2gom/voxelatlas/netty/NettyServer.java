package fr.boul2gom.voxelatlas.netty;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.server.core.io.netty.NettyUtil;
import fr.boul2gom.voxelatlas.VoxelAtlas;
import fr.boul2gom.voxelatlas.netty.network.HttpContext;
import fr.boul2gom.voxelatlas.netty.network.request.HttpRequest;
import fr.boul2gom.voxelatlas.netty.router.HttpRouter;
import fr.boul2gom.voxelatlas.netty.router.handlers.FilesHandler;
import fr.boul2gom.voxelatlas.netty.router.handlers.TilesHandler;
import fr.boul2gom.voxelatlas.netty.router.handlers.WorldsHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The main Netty HTTP server component.
 * <p>
 * This class orchestrates the startup and shutdown of the Netty server.
 * It initializes the main router, registers default handlers (files, tiles,
 * worlds, favicon),
 * and manages the Netty boss/worker thread groups.
 * </p>
 */
public class NettyServer {

    /** Global Gson instance configured for the server */
    public static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .create();

    /** The main plugin instance */
    private final VoxelAtlas plugin;
    /** The port to bind to */
    private final int port;

    /** Netty EventLoopGroup for accepting connections */
    private EventLoopGroup boss;
    /** Netty EventLoopGroup for handling IO */
    private EventLoopGroup worker;
    /** The active server channel */
    private Channel http_channel;

    /** Internal map of routers */
    private final Map<String, HttpRouter> routers;
    /** The main root router */
    private HttpRouter main_router;

    /**
     * Create a new Netty server instance.
     *
     * @param plugin The VoxelAtlas plugin instance.
     * @param port   The port number to listen on.
     */
    public NettyServer(VoxelAtlas plugin, int port) {
        this.plugin = plugin;
        this.port = port;

        this.routers = new HashMap<>();
    }

    /**
     * Starts the Netty server.
     * <p>
     * Initializes routes, binds the port, and starts accepting connections.
     * This method blocks until the bind is successful, but the server runs
     * asynchronously.
     * If binding fails, the application will exit.
     * </p>
     */
    public void start() {
        VoxelAtlas.LOGGER.atInfo().log("[VoxelAtlas] Initializing HTTP routes...");
        this.main_router = this.create_router("/");

        this.main_router.get("/", ((_, ctx) -> {
            try {
                final byte[] content = this.load_resource("/Server/index.html");
                if (content == null) {
                    ctx.error("Index page not found", HttpResponseStatus.NOT_FOUND);
                    return;
                }

                ctx.html(new String(content, StandardCharsets.UTF_8), HttpResponseStatus.OK);
            } catch (Exception e) {
                ctx.error("Failed to load index page", HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
        }));

        // Serve favicon.ico
        this.main_router.get("favicon.ico", ((_, ctx) -> {
            try {
                final byte[] content = this.load_resource("/Server/favicon.ico");
                if (content == null) {
                    ctx.error("Favicon not found", HttpResponseStatus.NOT_FOUND);
                    return;
                }
                ctx.send_binary(content, "image/x-icon", HttpResponseStatus.OK);
            } catch (Exception e) {
                ctx.error("Failed to load favicon", HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
        }));

        this.main_router.get("welcome", (_, ctx) -> ctx.text("Welcome on VoxelAtlas!"));

        new WorldsHandler(this.plugin);
        new TilesHandler(this.plugin);
        new FilesHandler(this.plugin);

        VoxelAtlas.LOGGER.atInfo().log("[VoxelAtlas] Starting HTTP server...");
        this.boss = NettyUtil.getEventLoopGroup(1, "VoxelAtlas - Netty -> Group: " + "boss");
        this.worker = NettyUtil.getEventLoopGroup(4, "VoxelAtlas - Netty -> Group: " + "worker");

        try {
            final ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(this.boss, this.worker)
                    .channel(NettyUtil.getServerChannel())
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new HttpServerHandler.Initializer(this.plugin));

            this.http_channel = bootstrap.bind("0.0.0.0", this.port).sync().channel();

            VoxelAtlas.LOGGER.atInfo().log("Listening on " + this.http_channel.localAddress().toString());
        } catch (InterruptedException e) {
            VoxelAtlas.LOGGER.atSevere().log("[VoxelAtlas] Failed to bind to port!");
            VoxelAtlas.LOGGER.atSevere().log(
                    "[VoxelAtlas] Make sure that no other applications are using the port given in the configuration.");
            VoxelAtlas.LOGGER.atSevere().log("[VoxelAtlas] Exception: " + e.getMessage());

            Thread.currentThread().interrupt();
            System.exit(-1);
        }
    }

    /**
     * Dispatches an incoming request to the appropriate router.
     * <p>
     * Parses the URI to determine the target path and delegates to the matching
     * router.
     * </p>
     *
     * @param request The HTTP request.
     * @param context The HTTP context.
     */
    public void dispatch(HttpRequest request, HttpContext context) {
        final int params_start = request.uri().indexOf("?");
        final String uri = request.uri().substring(0, params_start == -1 ? request.uri().length() : params_start);
        final String[] path = uri.split("((?=/))");

        String[] cleaned_path = path;
        if (path.length > 0 && path[0].isEmpty()) {
            cleaned_path = Arrays.copyOfRange(path, 1, path.length);
        }

        if (cleaned_path.length == 0) {
            final HttpRouter router = this.routers.get("/");
            if (router == null) {
                context.error("Invalid endpoint!", HttpResponseStatus.NOT_FOUND);
                return;
            }
            router.dispatch(new String[0], request, context);
            return;
        }

        HttpRouter router = this.routers.get(cleaned_path[0]);

        if (router == null) {
            router = this.routers.get("/");

            if (router == null) {
                context.error("Invalid endpoint!", HttpResponseStatus.NOT_FOUND);
                return;
            }

            router.dispatch(cleaned_path, request, context);
            return;
        }

        router.dispatch(Arrays.copyOfRange(cleaned_path, 1, cleaned_path.length), request, context);
    }

    /**
     * Shuts down the Netty server gracefully.
     * <p>
     * Closes the server channel and shuts down the worker/boss event loops.
     * </p>
     */
    public void shutdown() {
        if (this.http_channel != null) {
            this.http_channel.close();
        }

        if (this.worker != null) {
            this.worker.shutdownGracefully();
        }
        if (this.boss != null) {
            this.boss.shutdownGracefully();
        }
    }

    /**
     * Creates and registers a new top-level router.
     *
     * @param path The path for the router.
     * @return The created {@link HttpRouter}.
     */
    public HttpRouter create_router(String path) {
        final HttpRouter router = new HttpRouter(path);
        this.routers.put(path.toLowerCase(), router);

        return router;
    }

    /**
     * Retrieves a router by its path.
     *
     * @param path The path of the router.
     * @return The {@link HttpRouter} or null if not found.
     */
    public HttpRouter router_by_path(String path) {
        return this.routers.get(path.toLowerCase());
    }

    /**
     * Gets the main root router ("/");
     *
     * @return The main {@link HttpRouter}.
     */
    public HttpRouter main_router() {
        return this.main_router;
    }

    /**
     * Normalizes a URI string.
     * <p>
     * Trims whitespace, removes trailing slashes (unless root), and ensures a
     * leading slash.
     * </p>
     *
     * @param uri The URI to normalize.
     * @return The normalized URI string.
     */
    public static String normalize(String uri) {
        uri = uri.trim();

        while (uri.endsWith("/") && !uri.equals("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }

        if (!uri.startsWith("/")) {
            uri = "/" + uri;
        }
        return uri;
    }

    /**
     * Helper to load a resource file from classpath.
     *
     * @param path The resource path.
     * @return The file content as bytes, or null if not found.
     */
    private byte[] load_resource(String path) {
        try (java.io.InputStream is = this.getClass().getResourceAsStream(path)) {
            if (is == null)
                return null;
            return is.readAllBytes();
        } catch (java.io.IOException e) {
            return null;
        }
    }
}
