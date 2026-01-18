package fr.boul2gom.voxelaltas.netty.network.request;

import com.google.gson.JsonObject;
import fr.boul2gom.voxelaltas.netty.NettyServer;
import fr.boul2gom.voxelaltas.netty.network.HttpContext;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class HttpRequest {

    private final HttpContext ctx;

    private final String uri;
    private final HttpMethod method;
    private final HttpHeaders headers;
    private final ByteBuf content;

    private final List<HttpRequestParameter> parameters;

    public HttpRequest(FullHttpRequest request, HttpContext ctx, List<HttpRequestParameter> parameters) {
        this.ctx = ctx;
        this.parameters = parameters;
        this.uri = NettyServer.normalize(request.uri());
        this.method = request.method();
        this.headers = request.headers();
        this.content = request.content();
    }

    public String uri() {
        return this.uri;
    }

    public HttpMethod method() {
        return this.method;
    }

    public boolean has_parameter(String key) {
        return this.parameter(key) != null;
    }

    public HttpRequestParameter parameter(String key) {
        for (HttpRequestParameter parameter : this.parameters) {
            if (parameter.key().equalsIgnoreCase(key)) {
                return parameter;
            }
        }
        return null;
    }

    public List<HttpRequestParameter> parameters() {
        return this.parameters;
    }

    public HttpHeaders headers() {
        return this.headers;
    }

    public ByteBuf content() {
        return this.content;
    }

    public <T> T json_body(Class<T> body) {
        final String contentType = this.headers.get(HttpHeaderNames.CONTENT_TYPE);

        if (contentType == null || !contentType.contains("application/json")) {
            this.ctx.error("Bad Content-Type", HttpResponseStatus.BAD_REQUEST);
            return null;
        }

        final String json = this.content.toString(StandardCharsets.UTF_8);

        try {
            return NettyServer.GSON.fromJson(json, body);
        } catch (Exception e) {
            this.ctx.error("Bad Json", HttpResponseStatus.BAD_REQUEST);
            return null;
        }
    }

    public JsonObject json_body() {
        return this.json_body(JsonObject.class);
    }
}
