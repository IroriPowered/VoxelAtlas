package fr.boul2gom.voxelatlas.netty.network.request;

import com.google.gson.JsonObject;
import fr.boul2gom.voxelatlas.netty.NettyServer;
import fr.boul2gom.voxelatlas.netty.network.HttpContext;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Represents an incoming HTTP request.
 * <p>
 * This class wraps a Netty {@link FullHttpRequest} and provides simplified
 * access to
 * the URI, method, headers, content, and parsed query parameters.
 * </p>
 */
public class HttpRequest {

    /** The HTTP context associated with this request */
    private final HttpContext ctx;

    /** The normalized request URI */
    private final String uri;
    /** The HTTP method (GET, POST, etc.) */
    private final HttpMethod method;
    /** The request headers */
    private final HttpHeaders headers;
    /** The raw request content body */
    private final ByteBuf content;

    /** The list of parsed query parameters */
    private final List<HttpRequestParameter> parameters;

    /**
     * Create a new HTTP request wrapper.
     *
     * @param request    The raw Netty {@link FullHttpRequest}.
     * @param ctx        The {@link HttpContext} for sending responses.
     * @param parameters The list of parsed {@link HttpRequestParameter}s.
     */
    public HttpRequest(FullHttpRequest request, HttpContext ctx, List<HttpRequestParameter> parameters) {
        this.ctx = ctx;
        this.parameters = parameters;
        this.uri = NettyServer.normalize(request.uri());
        this.method = request.method();
        this.headers = request.headers();
        this.content = request.content();
    }

    /**
     * Gets the normalized request URI.
     *
     * @return The URI string.
     */
    public String uri() {
        return this.uri;
    }

    /**
     * Gets the HTTP method of the request.
     *
     * @return The {@link HttpMethod}.
     */
    public HttpMethod method() {
        return this.method;
    }

    /**
     * Checks if a query parameter exists.
     *
     * @param key The parameter key to check (case-insensitive).
     * @return True if the parameter exists, false otherwise.
     */
    public boolean has_parameter(String key) {
        return this.parameter(key) != null;
    }

    /**
     * Gets a query parameter by key.
     *
     * @param key The parameter key (case-insensitive).
     * @return The {@link HttpRequestParameter} if found, null otherwise.
     */
    public HttpRequestParameter parameter(String key) {
        for (HttpRequestParameter parameter : this.parameters) {
            if (parameter.key().equalsIgnoreCase(key)) {
                return parameter;
            }
        }
        return null;
    }

    /**
     * Gets the list of all parsed query parameters.
     *
     * @return The list of {@link HttpRequestParameter}s.
     */
    public List<HttpRequestParameter> parameters() {
        return this.parameters;
    }

    /**
     * Gets the request headers.
     *
     * @return The {@link HttpHeaders}.
     */
    public HttpHeaders headers() {
        return this.headers;
    }

    /**
     * Gets the raw content body of the request.
     *
     * @return The {@link ByteBuf} containing the content.
     */
    public ByteBuf content() {
        return this.content;
    }

    /**
     * Parses the request body as JSON.
     * <p>
     * Validates that the Content-Type header indicates JSON.
     * If parsing fails or the content type is invalid, it sends a standardized
     * error response via the context
     * and returns null.
     * </p>
     *
     * @param body The class to parse the JSON into.
     * @param <T>  The type of the body.
     * @return The parsed object, or null if validation or parsing failed.
     */
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

    /**
     * Parses the request body as a generic {@link JsonObject}.
     *
     * @return The parsed {@link JsonObject}, or null if validation or parsing
     *         failed.
     */
    public JsonObject json_body() {
        return this.json_body(JsonObject.class);
    }
}
