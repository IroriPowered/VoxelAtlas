package fr.boul2gom.voxelatlas.dynmap.cache.provider;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import fr.boul2gom.voxelatlas.VoxelAtlas;
import fr.boul2gom.voxelatlas.dynmap.cache.CacheType;
import fr.boul2gom.voxelatlas.dynmap.cache.CachedTile;
import fr.boul2gom.voxelatlas.dynmap.cache.TileCache;
import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder.Format;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

public class SQLiteTileCache implements TileCache {

    private final VoxelAtlas plugin;
    private final Path folder;
    private ConnectionSource connection;
    private Dao<CachedTile, Long> dao;

    public SQLiteTileCache(VoxelAtlas plugin) {
        this.plugin = plugin;
        this.folder = plugin.data_directory();
    }

    @Override
    public void init() {
        try {
            Files.createDirectories(this.folder);

            final String url = "jdbc:sqlite:" + folder.resolve("tiles.db").toAbsolutePath();
            this.connection = new JdbcConnectionSource(url);

            this.dao = DaoManager.createDao(this.connection, CachedTile.class);
            TableUtils.createTableIfNotExists(this.connection, CachedTile.class);

            VoxelAtlas.LOGGER.atInfo().log("[VoxelAtlas] SQLite cache initialized");
        } catch (SQLException | IOException e) {
            VoxelAtlas.LOGGER.atSevere().log("[VoxelAtlas] Failed to initialize SQLite cache: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public CompletableFuture<byte[]> get(String world, int zoom, int x, int z, Format format) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                CachedTile tile = this.dao.queryBuilder()
                        .where()
                        .eq("world", world)
                        .and()
                        .eq("zoom", zoom)
                        .and()
                        .eq("x", x)
                        .and()
                        .eq("z", z)
                        .and()
                        .eq("format", format)
                        .queryForFirst();

                return tile != null ? tile.data() : null;
            } catch (SQLException e) {
                VoxelAtlas.LOGGER.atSevere().log("[VoxelAtlas] Error fetching tile from SQLite: " + e.getMessage());
            }
            return null;
        });
    }

    @Override
    public void put(String world, int zoom, int x, int z, Format format, byte[] data) {
        CompletableFuture.runAsync(() -> {
            try {
                CachedTile existing = this.dao.queryBuilder()
                        .where()
                        .eq("world", world)
                        .and()
                        .eq("zoom", zoom)
                        .and()
                        .eq("x", x)
                        .and()
                        .eq("z", z)
                        .and()
                        .eq("format", format)
                        .queryForFirst();

                if (existing != null) {
                    existing.set_data(data);
                    this.dao.update(existing);
                } else {
                    CachedTile newTile = new CachedTile(world, zoom, x, z, format, data);
                    this.dao.create(newTile);
                }
            } catch (SQLException e) {
                VoxelAtlas.LOGGER.atSevere().log("[VoxelAtlas] Error saving tile to SQLite: " + e.getMessage());
            }
        });
    }

    @Override
    public boolean has(String world, int zoom, int x, int z, Format format) {
        try {
            return this.dao.queryBuilder()
                    .where()
                    .eq("world", world)
                    .and()
                    .eq("zoom", zoom)
                    .and()
                    .eq("x", x)
                    .and()
                    .eq("z", z)
                    .and()
                    .eq("format", format)
                    .countOf() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void close() {
        if (this.connection != null) {
            try {
                this.connection.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public CacheType type() {
        return CacheType.SQLITE;
    }
}
