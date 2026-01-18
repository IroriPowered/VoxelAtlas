package fr.boul2gom.voxelatlas.dynmap.cache.provider;

import fr.boul2gom.voxelatlas.dynmap.cache.CacheType;
import fr.boul2gom.voxelatlas.dynmap.cache.TileCache;
import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder.Format;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class MemoryTileCache implements TileCache {

    private final Map<String, byte[]> cache;
    private final int capacity;

    public MemoryTileCache(int capacity) {
        this.capacity = capacity;
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                return size() > MemoryTileCache.this.capacity;
            }
        });
    }

    @Override
    public CompletableFuture<byte[]> get(String world, int zoom, int x, int z, Format format) {
        final String key = create_key(world, zoom, x, z, format);
        final byte[] data = cache.get(key);
        if (data != null) {
            return CompletableFuture.completedFuture(data);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void put(String world, int zoom, int x, int z, Format format, byte[] data) {
        final String key = create_key(world, zoom, x, z, format);
        cache.put(key, data);
    }

    @Override
    public boolean has(String world, int zoom, int x, int z, Format format) {
        return cache.containsKey(create_key(world, zoom, x, z, format));
    }

    @Override
    public void init() {
        // No initialization needed for memory cache
    }

    @Override
    public void close() {
        cache.clear();
    }

    @Override
    public CacheType type() {
        return CacheType.MEMORY;
    }

    private String create_key(String world, int zoom, int x, int z, Format format) {
        return world + ":" + zoom + ":" + x + ":" + z + ":" + format;
    }
}
