package fr.boul2gom.voxelatlas.dynmap.cache;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import fr.boul2gom.voxelatlas.dynmap.encoder.ImageEncoder.Format;

@DatabaseTable(tableName = "tiles")
public class CachedTile {

    @DatabaseField(generatedId = true)
    private long id;

    @DatabaseField(uniqueCombo = true, index = true)
    private String world;

    @DatabaseField(uniqueCombo = true, index = true)
    private int zoom;

    @DatabaseField(uniqueCombo = true, index = true)
    private int x;

    @DatabaseField(uniqueCombo = true, index = true)
    private int z;

    @DatabaseField(uniqueCombo = true, index = true)
    private Format format;

    @DatabaseField(dataType = DataType.BYTE_ARRAY)
    private byte[] data;

    @DatabaseField
    private long timestamp;

    // Required by the ORM
    public CachedTile() {}

    public CachedTile(String world, int zoom, int x, int z, Format format, byte[] data) {
        this.world = world;
        this.zoom = zoom;
        this.x = x;
        this.z = z;
        this.format = format;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public long id() {
        return id;
    }

    public String world() {
        return world;
    }

    public int zoom() {
        return zoom;
    }

    public int x() {
        return x;
    }

    public int z() {
        return z;
    }

    public Format format() {
        return format;
    }

    public byte[] data() {
        return data;
    }

    public long timestamp() {
        return timestamp;
    }

    public void set_data(byte[] data) {
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }
}
