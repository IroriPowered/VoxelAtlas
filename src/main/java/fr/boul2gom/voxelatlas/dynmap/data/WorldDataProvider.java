package fr.boul2gom.voxelatlas.dynmap.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

/**
 * Provider for world and player data shared between HTTP and WebSocket handlers
 */
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;

public class WorldDataProvider {

    /**
     * Get all available worlds with their player counts
     * 
     * @return JsonArray of world data
     */
    public static JsonArray available_worlds() {
        final JsonArray worlds = new JsonArray();

        for (final World world : Universe.get().getWorlds().values()) {
            final JsonObject world_data = new JsonObject();
            world_data.addProperty("name", world.getName());
            world_data.addProperty("players", world.getPlayerCount());

            final Vector3d spawn = get_spawn(world);

            world_data.addProperty("spawn_x", spawn.x);
            world_data.addProperty("spawn_z", spawn.z);

            worlds.add(world_data);
        }

        return worlds;
    }

    public static Vector3d get_spawn(World world) {
        final ISpawnProvider provider = world.getWorldConfig().getSpawnProvider();

        if (provider != null) {
            final Transform global = provider.getSpawnPoint(world, world.getWorldConfig().getUuid());
            if (global != null)
                return global.getPosition();
        }

        return new Vector3d(0, 0, 0);
    }

    /**
     * Get all players in a specific world
     * 
     * @param world The world to get players from
     * @return JsonArray of player data
     */
    public static JsonArray available_players(World world) {
        final JsonArray players = new JsonArray();

        for (final PlayerRef player : world.getPlayerRefs()) {
            final JsonObject player_data = player_to_json(player);
            players.add(player_data);
        }

        return players;
    }

    /**
     * Convert a PlayerRef to a JsonObject
     * 
     * @param player The player reference
     * @return JsonObject with player data
     */
    private static JsonObject player_to_json(PlayerRef player) {
        final JsonObject player_data = new JsonObject();
        player_data.addProperty("name", player.getUsername());
        player_data.addProperty("uuid", player.getUuid().toString());

        final Transform transform = player.getTransform();
        final Vector3d pos = transform.getPosition();
        final Vector3f rot = transform.getRotation();

        player_data.addProperty("x", pos.x);
        player_data.addProperty("y", pos.y);
        player_data.addProperty("z", pos.z);
        player_data.addProperty("yaw", rot.y);

        return player_data;
    }
}
