package fr.boul2gom.voxelaltas.netty.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.boul2gom.voxelaltas.netty.NettyServer;

public record HttpResponse(JsonObject object) {

    public HttpResponse() {
        this(new JsonObject());
    }

    public HttpResponse add(String key, Object value) {
        this.object.add(key, NettyServer.GSON.toJsonTree(value));
        return this;
    }

    public HttpResponse add(String key, JsonElement element) {
        this.object.add(key, element);
        return this;
    }

    public HttpResponse add(String key, String value) {
        this.object.addProperty(key, value);
        return this;
    }

    public HttpResponse add(String key, boolean value) {
        this.object.addProperty(key, value);
        return this;
    }

    public HttpResponse add(String key, Number value) {
        this.object.addProperty(key, value);
        return this;
    }

    public HttpResponse add(String key, Character value) {
        this.object.addProperty(key, value);
        return this;
    }

    @Override
    public String toString() {
        return this.object.toString();
    }
}
