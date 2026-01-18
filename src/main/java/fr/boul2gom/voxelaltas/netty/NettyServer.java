package fr.boul2gom.voxelaltas.netty;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.server.core.io.netty.NettyUtil;
import fr.boul2gom.voxelaltas.VoxelAtlas;
import fr.boul2gom.voxelaltas.netty.network.HttpContext;
import fr.boul2gom.voxelaltas.netty.network.request.HttpRequest;
import fr.boul2gom.voxelaltas.netty.router.HttpRouter;
import fr.boul2gom.voxelaltas.netty.router.handlers.FilesHandler;
import fr.boul2gom.voxelaltas.netty.router.handlers.TilesHandler;
import fr.boul2gom.voxelaltas.netty.router.handlers.WorldsHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class NettyServer {

    public static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .create();

    private final VoxelAtlas plugin;
    private final int port;

    private EventLoopGroup boss;
    private EventLoopGroup worker;
    private Channel http_channel;

    private final Map<String, HttpRouter> routers;
    private HttpRouter main_router;

    public NettyServer(VoxelAtlas plugin, int port) {
        this.plugin = plugin;
        this.port = port;

        this.routers = new HashMap<>();
    }

    public void start() {
        System.out.println("[VoxelAtlas] Initializing HTTP routes...");
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

        System.out.println("[VoxelAtlas] Starting HTTP server...");
        this.boss = NettyUtil.getEventLoopGroup(1, "VoxelAtlas - Netty -> Group: " + "boss");
        this.worker = NettyUtil.getEventLoopGroup(4, "VoxelAtlas - Netty -> Group: " + "worker");

        try {
            final ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(this.boss, this.worker)
                    .channel(NettyUtil.getServerChannel())
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new HttpServerHandler.Initializer(this.plugin));

            this.http_channel = bootstrap.bind("0.0.0.0", this.port).sync().channel();

            System.out.println("Listening on " + this.http_channel.localAddress().toString());
        } catch (InterruptedException e) {
            System.err.println("[VoxelAtlas] Failed to bind to port!");
            System.err.println("[VoxelAtlas] Make sure that no other applications are using the port given in the configuration.");
            System.err.println("[VoxelAtlas] Exception: " + e.getMessage());

            Thread.currentThread().interrupt();
            System.exit(-1);
        }
    }

    public void dispatch(HttpRequest request, HttpContext context) {
        final int params_start = request.uri().indexOf("?");
        final String uri = request.uri().substring(0, params_start == -1 ? request.uri().length() : params_start);
        final String[] path = uri.split("((?=/))");

        System.out.println("[VoxelAtlas] Dispatching URI: " + uri);
        System.out.println("[VoxelAtlas] Split path: " + Arrays.toString(path));

        String[] cleaned_path = path;
        if (path.length > 0 && path[0].isEmpty()) {
            cleaned_path = Arrays.copyOfRange(path, 1, path.length);
            System.out.println("[VoxelAtlas] Cleaned path: " + Arrays.toString(cleaned_path));
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

    public HttpRouter create_router(String path) {
        final HttpRouter router = new HttpRouter(path);
        this.routers.put(path.toLowerCase(), router);

        return router;
    }

    public HttpRouter router_by_path(String path) {
        return this.routers.get(path.toLowerCase());
    }

    public HttpRouter main_router() {
        return this.main_router;
    }

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

    private byte[] load_resource(String path) {
        try (java.io.InputStream is = this.getClass().getResourceAsStream(path)) {
            if (is == null) return null;
            return is.readAllBytes();
        } catch (java.io.IOException e) {
            return null;
        }
    }
}
