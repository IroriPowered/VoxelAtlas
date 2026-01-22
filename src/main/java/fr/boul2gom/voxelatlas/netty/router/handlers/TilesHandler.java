package fr.boul2gom.voxelatlas.netty.router.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.boul2gom.voxelatlas.VoxelAtlas;
import fr.boul2gom.voxelatlas.dynmap.cache.provider.FileSystemTileCache;
import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder.Format;
import fr.boul2gom.voxelatlas.dynmap.renderer.RendererType;
import fr.boul2gom.voxelatlas.netty.router.HttpRouter;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.io.File;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handles HTTP requests for map tiles.
 * <p>
 * This handler processes GET requests for individual tiles and POST requests
 * for batch tiles.
 * It interfaces with the {@link fr.boul2gom.voxelatlas.dynmap.TileManager} to
 * fetch or generate tiles.
 * It also supports Zero-Copy file transfer for cached tiles on the filesystem.
 * </p>
 */
public class TilesHandler {

    /** Maximum number of tiles allowed in a single batch request */
    private static final int MAX_TILES = 200;

    /**
     * Registers the tiles handler routes.
     * <p>
     * Sets up:
     * <ul>
     * <li>GET /tiles/ - for fetching a single tile.</li>
     * <li>POST /tiles/ - for fetching a batch of tiles.</li>
     * </ul>
     * </p>
     *
     * @param plugin The VoxelAtlas plugin instance.
     */
    public TilesHandler(VoxelAtlas plugin) {
        final HttpRouter main = plugin.netty().main_router();
        final HttpRouter router = main.child_router("/tiles");

        router.get("/", ((request, ctx) -> {
            try {
                if (!request.has_parameter("world")) {
                    ctx.error("Missing parameter 'world'!", HttpResponseStatus.BAD_REQUEST);
                    return;
                }
                if (!request.has_parameter("zoom")) {
                    ctx.error("Missing parameter 'zoom'!", HttpResponseStatus.BAD_REQUEST);
                    return;
                }
                if (!request.has_parameter("x")) {
                    ctx.error("Missing parameter 'x'!", HttpResponseStatus.BAD_REQUEST);
                    return;
                }
                if (!request.has_parameter("z")) {
                    ctx.error("Missing parameter 'z'!", HttpResponseStatus.BAD_REQUEST);
                    return;
                }

                if (!request.has_parameter("renderer")) {
                    ctx.error("Missing parameter 'renderer'!", HttpResponseStatus.BAD_REQUEST);
                    return;
                }

                final String name = request.parameter("world").first_value();
                final int zoom = Integer.parseInt(request.parameter("zoom").first_value());
                final int x = Integer.parseInt(request.parameter("x").first_value());
                final int z = Integer.parseInt(request.parameter("z").first_value());
                final RendererType renderer = RendererType.from_id(request.parameter("renderer").first_value());

                // Zero-Copy Optimization check
                if (plugin.tiles().persistent_cache() instanceof FileSystemTileCache file_cache) {
                    final File file = file_cache.get_file(name, zoom, x, z, Format.PNG, renderer.name()).toFile();
                    if (file.exists()) {
                        ctx.send_file(file, "image/png");
                        return;
                    }
                }

                // Send response with appropriate content type
                plugin.tiles().fetch_tile(name, zoom, x, z, Format.PNG, renderer, true, false).thenAccept(ctx::png);
            } catch (Exception e) {
                ctx.error("Invalid request!", HttpResponseStatus.BAD_REQUEST);
            }
        }));

        router.post("/", ((request, ctx) -> {
            try {
                final JsonObject body = request.json_body();
                if (!body.has("world") || !body.has("tiles")) {
                    ctx.error("Invalid request!", HttpResponseStatus.BAD_REQUEST);
                    return;
                }

                final String world = body.get("world").getAsString();
                final JsonArray tiles = body.get("tiles").getAsJsonArray();
                if (tiles.size() > MAX_TILES) {
                    ctx.error("Too many tiles!", HttpResponseStatus.BAD_REQUEST);
                    return;
                }

                // Support optional format parameter (default: PNG)
                // Support optional renderer parameter (default: FLAT)
                RendererType renderer = RendererType.FLAT;
                if (body.has("renderer")) {
                    renderer = RendererType.from_id(body.get("renderer").getAsString());
                }

                final Map<String, CompletableFuture<byte[]>> futures = new LinkedHashMap<>();

                for (int i = 0; i < tiles.size(); ++i) {
                    final JsonObject tile = tiles.get(i).getAsJsonObject();
                    final int zoom = tile.get("zoom").getAsInt();
                    final int x = tile.get("x").getAsInt();
                    final int z = tile.get("z").getAsInt();

                    final String key = zoom + "/" + x + "/" + z;
                    futures.put(key, plugin.tiles().fetch_tile(world, zoom, x, z, Format.PNG, renderer, true, false));
                }

                CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0])).thenAccept(v -> {
                    final JsonObject tiles_response = new JsonObject();

                    for (Map.Entry<String, CompletableFuture<byte[]>> entry : futures.entrySet()) {
                        final byte[] data = entry.getValue().join();
                        final JsonObject tile_json = new JsonObject();

                        if (this.is_empty(data)) {
                            tile_json.addProperty("empty", Boolean.TRUE);
                        } else {
                            tile_json.addProperty("data", Base64.getEncoder().encodeToString(data));
                        }

                        tiles_response.add(entry.getKey(), tile_json);
                    }

                    ctx.json(response -> response.add("tiles", tiles_response));
                });
            } catch (Exception e) {
                ctx.error("Invalid request!", HttpResponseStatus.BAD_REQUEST);
            }
        }));
    }

    /**
     * Checks if a tile data array is considered "empty".
     *
     * @param data The tile data byte array.
     * @return True if the data is null or smaller than the threshold (500 bytes).
     */
    private boolean is_empty(byte[] data) {
        return data == null || data.length < 500;
    }
}
