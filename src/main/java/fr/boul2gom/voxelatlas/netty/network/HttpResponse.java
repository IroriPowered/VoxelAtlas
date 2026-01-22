package fr.boul2gom.voxelatlas.netty.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.boul2gom.voxelatlas.netty.NettyServer;
import org.jetbrains.annotations.NotNull;

/**
 * A builder wrapper for constructing JSON HTTP responses.
 * <p>
 * This record wraps a Google Gson {@link JsonObject} and provides fluent
 * methods
 * to add properties of various types before serialization.
 * </p>
 * <p>
 * Constructor parameters:
 * <ul>
 * <li><b>object</b>: The internal {@link JsonObject} being built.</li>
 * </ul>
 * </p>
 */
public record HttpResponse(
    /** The internal JSON object storing response data */
    JsonObject object
) {

    /**
     * Creates a new empty HTTP response builder.
     */
    public HttpResponse() {
        this(new JsonObject());
    }

    /**
     * Adds an arbitrary object to the response.
     *
     * @param key   The property key.
     * @param value The object value (serialized using Gson).
     * @return This builder instance for chaining.
     */
    public HttpResponse add(String key, Object value) {
        this.object.add(key, NettyServer.GSON.toJsonTree(value));
        return this;
    }

    /**
     * Adds a {@link JsonElement} to the response.
     *
     * @param key     The property key.
     * @param element The JSON element to add.
     * @return This builder instance for chaining.
     */
    public HttpResponse add(String key, JsonElement element) {
        this.object.add(key, element);
        return this;
    }

    /**
     * Adds a string property to the response.
     *
     * @param key   The property key.
     * @param value The string value.
     * @return This builder instance for chaining.
     */
    public HttpResponse add(String key, String value) {
        this.object.addProperty(key, value);
        return this;
    }

    /**
     * Adds a boolean property to the response.
     *
     * @param key   The property key.
     * @param value The boolean value.
     * @return This builder instance for chaining.
     */
    public HttpResponse add(String key, boolean value) {
        this.object.addProperty(key, value);
        return this;
    }

    /**
     * Adds a numeric property to the response.
     *
     * @param key   The property key.
     * @param value The numeric value.
     * @return This builder instance for chaining.
     */
    public HttpResponse add(String key, Number value) {
        this.object.addProperty(key, value);
        return this;
    }

    /**
     * Adds a character property to the response.
     *
     * @param key   The property key.
     * @param value The character value.
     * @return This builder instance for chaining.
     */
    public HttpResponse add(String key, Character value) {
        this.object.addProperty(key, value);
        return this;
    }

    /**
     * serialized this response to a JSON string.
     *
     * @return The JSON string representation.
     */
    @NotNull
    @Override
    public String toString() {
        return this.object.toString();
    }
}
