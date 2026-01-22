package fr.boul2gom.voxelatlas.utils;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Plugin configuration class.
 * <p>
 * This class uses Hytale's {@link BuilderCodec} to serialize/deserialize
 * configuration options.
 * It contains settings for the web server, pre-generation, caching, and view
 * radius.
 * </p>
 */
public class Configuration {

    /** The decoder schema for the configuration file */
    public static final BuilderCodec<Configuration> CODEC = BuilderCodec
            .builder(Configuration.class, Configuration::new)
            .append(new KeyedCodec<>("WebserverPort", Codec.INTEGER),
                    (config, value, info) -> config.webserver_port = value,
                    (config, info) -> config.webserver_port)
            .add()
            .append(new KeyedCodec<>("Pregeneration", Codec.BOOLEAN),
                    (config, value, info) -> config.pregenerate = value,
                    (config, info) -> config.pregenerate)
            .add()
            .append(new KeyedCodec<>("PregenRadius", Codec.INTEGER),
                    (config, value, info) -> config.pregen_radius = value,
                    (config, info) -> config.pregen_radius)
            .add()
            .append(new KeyedCodec<>("DisplayUnexplored", Codec.BOOLEAN),
                    (config, value, info) -> config.display_unexplored = value,
                    (config, info) -> config.display_unexplored)
            .add()
            .append(new KeyedCodec<>("CacheExpirationHours", Codec.INTEGER),
                    (config, value, info) -> config.cache_expiration_hours = value,
                    (config, info) -> config.cache_expiration_hours)
            .add()
            .append(new KeyedCodec<>("ViewRadius", Codec.INTEGER),
                    (config, value, info) -> config.view_radius = value,
                    (config, info) -> config.view_radius)
            .add()
            .append(new KeyedCodec<>("ViewUpdatePeriod", Codec.INTEGER),
                    (config, value, info) -> config.view_update_period = value,
                    (config, info) -> config.view_update_period)
            .add()
            .append(new KeyedCodec<>("SpawnRadius", Codec.INTEGER),
                    (config, value, info) -> config.spawn_radius = value,
                    (config, info) -> config.spawn_radius)
            .add()
            .append(new KeyedCodec<>("CacheType", Codec.STRING),
                    (config, value, info) -> config.cache_type = value,
                    (config, info) -> config.cache_type)
            .add()
            .build();

    /** The port for the web server */
    private int webserver_port = 8080;

    /** Whether to pre-generate chunks on first start */
    private boolean pregenerate = true;
    /** The radius (in chunks) for pre-generation */
    private int pregen_radius = 10;

    /** Whether to display unexplored/empty chunks */
    private boolean display_unexplored = false;

    /** Cache expiration time in hours (default: 1 week) */
    private int cache_expiration_hours = 168;

    /** Radius (in chunks) around players to update dynamically */
    private int view_radius = 5;
    /** Frequency (in seconds) of view updates */
    private int view_update_period = 30;

    /** Radius (in chunks) around the world spawn to render default */
    private int spawn_radius = 10;
    /** The cache backend type (e.g., "filesystem", "memory") */
    private String cache_type = "filesystem";

    /**
     * Default constructor required for instantiation.
     */
    public Configuration() {}

    /**
     * Gets the configured cache backend type ID.
     *
     * @return The cache type string.
     */
    public String cache_type() {
        return this.cache_type;
    }

    /**
     * Gets the configured web server port.
     *
     * @return The port number.
     */
    public int webserver_port() {
        return this.webserver_port;
    }

    /**
     * Checks if pre-generation is enabled.
     *
     * @return True if enabled, false otherwise.
     */
    public boolean pregenerate() {
        return this.pregenerate;
    }

    /**
     * Gets the radius for pre-generation.
     *
     * @return The radius in chunks.
     */
    public int pregen_radius() {
        return this.pregen_radius;
    }

    /**
     * Checks if unexplored chunks should be displayed.
     *
     * @return True if enabled, false otherwise.
     */
    public boolean display_unexplored() {
        return this.display_unexplored;
    }

    /**
     * Gets the cache expiration time.
     *
     * @return The expiration time in hours.
     */
    public int cache_expiration_hours() {
        return this.cache_expiration_hours;
    }

    /**
     * Gets the dynamic view radius around players.
     *
     * @return The radius in chunks.
     */
    public int view_radius() {
        return this.view_radius;
    }

    /**
     * Gets the update period for dynamic views.
     *
     * @return The period in seconds.
     */
    public int view_update_period() {
        return this.view_update_period;
    }

    /**
     * Gets the spawn area radius.
     *
     * @return The radius in chunks.
     */
    public int spawn_radius() {
        return this.spawn_radius;
    }
}
