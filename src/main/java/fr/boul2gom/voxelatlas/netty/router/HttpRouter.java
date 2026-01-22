package fr.boul2gom.voxelatlas.netty.router;

import fr.boul2gom.voxelatlas.VoxelAtlas;
import fr.boul2gom.voxelatlas.netty.network.HttpContext;
import fr.boul2gom.voxelatlas.netty.network.request.HttpRequest;
import fr.boul2gom.voxelatlas.netty.network.request.IHttpRequestHandler;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple HTTP router for dispatching requests to handlers.
 * <p>
 * This class organizes request handlers by path and method.
 * It supports nested (child) routers for organizing routes hierarchically
 * (e.g., /api/v1/...).
 * </p>
 */
public class HttpRouter {

    /** Sub-routers mapped by their path segment */
    private final Map<String, HttpRouter> routers;
    /** Handlers mapped by HTTP method and path */
    private final Map<HttpMethod, Map<String, IHttpRequestHandler>> handlers;

    /** The base path of this router */
    private final String path;

    /**
     * Create a new router with the specified base path.
     *
     * @param path The base path segment for this router.
     */
    public HttpRouter(String path) {
        this.path = path;
        this.routers = new HashMap<>();
        this.handlers = new HashMap<>();
    }

    /**
     * Dispatches an incoming request to the appropriate handler.
     * <p>
     * Recursively traverses child routers if the path matches, otherwise looks for
     * a handler
     * registered on the current router.
     * </p>
     *
     * @param path    The remaining path segments to match.
     * @param request The HTTP request.
     * @param context The HTTP context.
     */
    public void dispatch(String[] path, HttpRequest request, HttpContext context) {
        if (path.length == 0) {
            final Map<String, IHttpRequestHandler> handlers = this.handlers.get(request.method());

            if (handlers != null) {
                IHttpRequestHandler handler = handlers.get("/");
                if (handler == null) {
                    handler = handlers.get("");
                }

                if (handler != null) {
                    handler.on_request(request, context);
                    return;
                }
            }

            context.error("Invalid endpoint", HttpResponseStatus.NOT_FOUND);
            return;
        }

        String[] cleaned_path = new String[path.length];
        for (int i = 0; i < path.length; i++) {
            cleaned_path[i] = path[i].startsWith("/") ? path[i].substring(1) : path[i];
        }

        final HttpRouter router = this.routers.get("/" + cleaned_path[0]);

        if (router == null) {
            final Map<String, IHttpRequestHandler> handlers = this.handlers.get(request.method());

            if (handlers != null) {
                final String joined_path = String.join("/", cleaned_path);
                final IHttpRequestHandler handler = handlers.get(joined_path);

                if (handler != null) {
                    handler.on_request(request, context);
                    return;
                }
            }

            context.error("Invalid endpoint!", HttpResponseStatus.NOT_FOUND);
            return;
        }

        router.dispatch(Arrays.copyOfRange(cleaned_path, 1, cleaned_path.length), request, context);
    }

    /**
     * Creates and registers a child router.
     *
     * @param path The path segment for the child router (e.g., "/api").
     * @return The created {@link HttpRouter}.
     */
    public HttpRouter child_router(String path) {
        final HttpRouter router = new HttpRouter(path);
        this.routers.put(path.toLowerCase(), router);

        return router;
    }

    /**
     * Registers a GET handler for a specific path.
     *
     * @param path    The path to match (relative to this router).
     * @param handler The handler to execute.
     */
    public void get(String path, IHttpRequestHandler handler) {
        final Map<String, IHttpRequestHandler> handlers = this.handlers.getOrDefault(HttpMethod.GET, new HashMap<>());
        VoxelAtlas.LOGGER.atInfo().log("[VoxelAtlas] Getting handler for path: " + this.path + path);
        handlers.put(path, handler);

        this.handlers.put(HttpMethod.GET, handlers);
    }

    /**
     * Registers a POST handler for a specific path.
     *
     * @param path    The path to match (relative to this router).
     * @param handler The handler to execute.
     */
    public void post(String path, IHttpRequestHandler handler) {
        final Map<String, IHttpRequestHandler> handlers = this.handlers.getOrDefault(HttpMethod.POST, new HashMap<>());
        VoxelAtlas.LOGGER.atInfo().log("[VoxelAtlas] Posting handler for path: " + this.path + path);
        handlers.put(path, handler);

        this.handlers.put(HttpMethod.POST, handlers);
    }

    /**
     * Gets the base path of this router.
     *
     * @return The path string.
     */
    public String path() {
        return this.path;
    }
}
