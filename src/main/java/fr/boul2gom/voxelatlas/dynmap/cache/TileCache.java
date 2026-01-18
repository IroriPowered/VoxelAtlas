package fr.boul2gom.voxelatlas.dynmap.cache;

import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder.Format;

import java.util.concurrent.CompletableFuture;

public interface TileCache {

    /**
     * Get a tile from the cache
     * 
     * @param world  World name
     * @param zoom   Zoom level
     * @param x      Tile X coordinate
     * @param z      Tile Z coordinate
     * @param format Image format
     * @return CompletableFuture containing the tile data, or null if not found
     */
    CompletableFuture<byte[]> get(String world, int zoom, int x, int z, Format format);

    /**
     * Put a tile into the cache
     * 
     * @param world  World name
     * @param zoom   Zoom level
     * @param x      Tile X coordinate
     * @param z      Tile Z coordinate
     * @param format Image format
     * @param data   Tile image data
     */
    void put(String world, int zoom, int x, int z, Format format, byte[] data);

    /**
     * Check if a tile exists in the cache
     * 
     * @param world  World name
     * @param zoom   Zoom level
     * @param x      Tile X coordinate
     * @param z      Tile Z coordinate
     * @param format Image format
     * @return true if the tile exists
     */
    boolean has(String world, int zoom, int x, int z, Format format);

    /**
     * Initialize the cache
     */
    void init();

    /**
     * Close the cache and release resources
     */
    void close();

    /**
     * Get the cache type
     * 
     * @return CacheType
     */
    CacheType type();
}
