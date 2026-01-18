package fr.boul2gom.hymap.netty.router.handlers;

import com.google.gson.JsonArray;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import fr.boul2gom.hymap.HytaleMap;
import fr.boul2gom.hymap.dynmap.data.WorldDataProvider;
import fr.boul2gom.hymap.netty.router.HttpRouter;
import io.netty.handler.codec.http.HttpResponseStatus;

public class WorldsHandler {

    public WorldsHandler(HytaleMap plugin) {
        final HttpRouter main = plugin.netty().main_router();
        final HttpRouter router = main.child_router("/worlds");

        router.get("/", (((_, ctx) -> {
            final JsonArray worlds = WorldDataProvider.available_worlds();
            ctx.json(response -> response.add("worlds", worlds));
        })));

        router.get("/players", ((request, ctx) -> {
            try {
                if (request.has_parameter("world")) {
                    final String name = request.parameter("world").first_value();
                    final World world = Universe.get().getWorld(name);

                    if (world == null) {
                        ctx.error("Invalid world!", HttpResponseStatus.NOT_FOUND);
                        return;
                    }

                    final JsonArray players = WorldDataProvider.available_players(world);
                    ctx.json(response -> response.add("players", players));

                    return;
                }

                throw new IllegalArgumentException();
            } catch (Exception e) {
                ctx.error("Invalid request!", HttpResponseStatus.BAD_REQUEST);
            }
        }));
    }
}
