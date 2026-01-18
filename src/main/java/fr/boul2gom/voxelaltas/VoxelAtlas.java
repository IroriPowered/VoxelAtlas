package fr.boul2gom.voxelaltas;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import fr.boul2gom.voxelaltas.dynmap.PlayerTracker;
import fr.boul2gom.voxelaltas.dynmap.TileManager;
import fr.boul2gom.voxelaltas.netty.NettyServer;

import javax.annotation.Nonnull;

public class VoxelAtlas extends JavaPlugin {

    private TileManager tiles;
    private PlayerTracker tracker;

    private NettyServer netty;

    public VoxelAtlas(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    public void setup() {
        this.tiles = new TileManager(this);
        this.tracker = new PlayerTracker(this);

        this.netty = new NettyServer(this, 8080);
    }

    @Override
    public void start() {
        this.netty.start();
        this.tracker.start();
    }

    @Override
    public void shutdown() {
        if (this.tracker != null) {
            this.tracker.shutdown();
        }
        if (this.netty != null) {
            this.netty.shutdown();
        }
    }

    public TileManager tiles() {
        return this.tiles;
    }

    public PlayerTracker tracker() {
        return this.tracker;
    }

    public NettyServer netty() {
        return this.netty;
    }
}
