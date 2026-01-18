package fr.boul2gom.voxelatlas.utils;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class Configuration {

    public static final BuilderCodec<Configuration> CODEC =
            BuilderCodec.builder(Configuration.class, Configuration::new)
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
                    .build();

    private int webserver_port = 8080;

    private boolean pregenerate = true;
    private int pregen_radius = 10;

    private boolean display_unexplored = true;

    public Configuration() {}

    public int webserver_port() {
        return this.webserver_port;
    }

    public boolean pregenerate() {
        return this.pregenerate;
    }

    public int pregen_radius() {
        return this.pregen_radius;
    }

    public boolean display_unexplored() {
        return this.display_unexplored;
    }
}
