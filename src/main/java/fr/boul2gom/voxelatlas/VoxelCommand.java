package fr.boul2gom.voxelatlas;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import fr.boul2gom.voxelatlas.dynmap.data.WorldDataProvider;
import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class VoxelCommand extends AbstractCommand {

    private final VoxelAtlas plugin;
    private final RequiredArg<String> subcommand;

    public VoxelCommand(VoxelAtlas plugin) {
        super("atlas", "VoxelAtlas admin commands");
        this.plugin = plugin;

        final ArgumentType<String> action = ArgTypes.STRING;
        this.subcommand = this.withRequiredArg("action", "status|pregen", action);
        this.requirePermission("voxelatlas.admin");
    }

    @Override
    protected CompletableFuture<Void> execute(@NotNull CommandContext ctx) {
        final CommandSender sender = ctx.sender();
        final String action = this.subcommand.get(ctx);
        final String[] parts = action.split(" ");
        final String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "status" -> this.show_status(sender);
            case "pregen" -> this.pregenerate(sender, parts);
            default -> this.show_help(sender);
        }

        return CompletableFuture.completedFuture(null);
    }

    private void show_status(CommandSender sender) {
        final int connections = this.plugin.websocket().connections_count();
        final int port = this.plugin.config().get().webserver_port();
        final String cache_type = this.plugin.config().get().cache_type();

        this.send_header(sender, "VoxelAtlas Status");
        this.send_message(sender, "HTTP server", "Running on port &a" + port);
        this.send_message(sender, "WebSocket", "&a" + connections + " &fconnections");
        this.send_message(sender, "Cache Type", "&a" + cache_type);
        this.send_message(sender, "URL", "&ahttp://localhost:" + port);
        this.send_footer(sender);
    }

    private void pregenerate(CommandSender sender, String[] parts) {
        // Usage: /atlas pregen <target> <radius>
        if (parts.length < 3) {
            sender.sendMessage(Message.raw("&cUsage: /atlas pregen <spawn|player> <radius>"));
            return;
        }

        final String target = parts[1];
        int radius;
        try {
            radius = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Message.raw("&cInvalid radius. It must be a number."));
            return;
        }

        if (radius < 1) {
            sender.sendMessage(Message.raw("&cRadius must be at least 1."));
            return;
        }

        int center_x = 0;
        int center_z = 0;
        String world_name = "default";

        // If sender is a player, we can default to their world if needed, but logic below handles targets
        if (sender instanceof PlayerRef player_sender) {
            if (player_sender.getWorldUuid() != null) {
                final World w = Universe.get().getWorld(player_sender.getWorldUuid());
                if (w != null) world_name = w.getName();
            }
        }

        if (target.equalsIgnoreCase("spawn")) {
            final World world = Universe.get().getWorld(world_name);
            if (world == null) {
                sender.sendMessage(Message.raw("&cCould not determine world."));
                return;
            }

            final Vector3d spawn = WorldDataProvider.get_spawn(world);
            center_x = ChunkUtil.chunkCoordinate((int) spawn.x);
            center_z = ChunkUtil.chunkCoordinate((int) spawn.z);

        } else {
            final Optional<PlayerRef> player = Universe.get().getPlayers().stream()
                    .filter(p -> p.getUsername().equalsIgnoreCase(target))
                    .findFirst();

            if (player.isEmpty()) {
                sender.sendMessage(Message.raw("&cPlayer '" + target + "' not found."));
                return;
            }

            final PlayerRef target_player = player.get();

            if (target_player.getWorldUuid() != null) {
                final World w = Universe.get().getWorld(target_player.getWorldUuid());
                if (w != null) world_name = w.getName();
            }

            final Vector3d pos = target_player.getTransform().getPosition();
            center_x = ChunkUtil.chunkCoordinate((int) pos.x);
            center_z = ChunkUtil.chunkCoordinate((int) pos.z);
        }

        sender.sendMessage(Message.raw("&eStarting pre-generation of &6" + ((radius * 2 + 1) * (radius * 2 + 1))
                + " &etiles around " + target + " in world &b" + world_name + "&e..."));
        sender.sendMessage(Message.raw("&7This runs in the background."));

        this.plugin.tiles().pregenerate(world_name, center_x, center_z, radius, ImageEncoder.Format.PNG)
                .thenAccept(count -> sender
                        .sendMessage(Message.raw("&aPre-generation complete! Generated &6" + count + " &anew tiles.")));
    }

    private void show_help(CommandSender sender) {
        this.send_header(sender, "VoxelAtlas Commands");
        this.send_message(sender, "/atlas status", "Show server status");
        this.send_message(sender, "/atlas pregen spawn <radius>", "Pre-generate around spawn");
        this.send_message(sender, "/atlas pregen <player> <radius>", "Pre-generate around player");
        this.send_footer(sender);
    }

    // -- Formatting Helpers --

    private void send_header(CommandSender sender, String title) {
        sender.sendMessage(Message.raw("&6&m--------------------------------------------------"));
        // Basic centering attempt (approximate)
        int padding = (50 - title.length()) / 2;
        String spaces = " ".repeat(Math.max(0, padding));
        sender.sendMessage(Message.raw("&6" + spaces + title));
        sender.sendMessage(Message.raw("&6&m--------------------------------------------------"));
    }

    private void send_footer(CommandSender sender) {
        sender.sendMessage(Message.raw("&6&m--------------------------------------------------"));
    }

    private void send_message(CommandSender sender, String key, String value) {
        sender.sendMessage(Message.raw("&f " + key + ": &7" + value));
    }
}
