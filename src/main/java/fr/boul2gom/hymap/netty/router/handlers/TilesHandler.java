package fr.boul2gom.hymap.netty.router.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.boul2gom.hymap.HytaleMap;
import fr.boul2gom.hymap.dynmap.encoder.ImageEncoder.Format;
import fr.boul2gom.hymap.netty.router.HttpRouter;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TilesHandler {

    private static final int MAX_TILES = 200;

    public TilesHandler(HytaleMap plugin) {
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

                final String name = request.parameter("world").first_value();
                final int zoom = Integer.parseInt(request.parameter("zoom").first_value());
                final int x = Integer.parseInt(request.parameter("x").first_value());
                final int z = Integer.parseInt(request.parameter("z").first_value());

                // Support optional format parameter (default: PNG)
                Format format = Format.PNG;
                if (request.has_parameter("format")) {
                    format = Format.fromString(request.parameter("format").first_value());
                }

                final Format finalFormat = format;
                plugin.tiles().fetch_tile(name, zoom, x, z, format).thenAccept(data -> {
                    // Send response with appropriate content type
                    if (finalFormat == Format.WEBP) {
                        ctx.webp(data);
                    } else {
                        ctx.png(data);
                    }
                });
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
                Format format = Format.PNG;
                if (body.has("format")) {
                    format = Format.fromString(body.get("format").getAsString());
                }

                final Map<String, CompletableFuture<byte[]>> futures = new LinkedHashMap<>();

                for (int i = 0; i < tiles.size(); ++i) {
                    final JsonObject tile = tiles.get(i).getAsJsonObject();
                    final int zoom = tile.get("zoom").getAsInt();
                    final int x = tile.get("x").getAsInt();
                    final int z = tile.get("z").getAsInt();

                    final String key = zoom + "/" + x + "/" + z;

                    futures.put(key, plugin.tiles().fetch_tile(world, zoom, x, z, format));
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

    private boolean is_empty(byte[] data) {
        return data == null || data.length < 500;
    }

    private record TileCoord(int zoom, int x, int z) {}
}
