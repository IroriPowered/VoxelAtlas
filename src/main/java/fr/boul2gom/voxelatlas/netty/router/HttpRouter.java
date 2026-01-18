package fr.boul2gom.voxelatlas.netty.router;

import fr.boul2gom.voxelatlas.netty.network.HttpContext;
import fr.boul2gom.voxelatlas.netty.network.request.HttpRequest;
import fr.boul2gom.voxelatlas.netty.network.request.IHttpRequestHandler;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class HttpRouter {

    private final Map<String, HttpRouter> routers;
    private final Map<HttpMethod, Map<String, IHttpRequestHandler>> handlers;

    private final String path;

    public HttpRouter(String path) {
        this.path = path;
        this.routers = new HashMap<>();
        this.handlers = new HashMap<>();
    }

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

    public HttpRouter child_router(String path) {
        final HttpRouter router = new HttpRouter(path);
        this.routers.put(path.toLowerCase(), router);

        return router;
    }

    public void get(String path, IHttpRequestHandler handler) {
        final Map<String, IHttpRequestHandler> handlers = this.handlers.getOrDefault(HttpMethod.GET, new HashMap<>());
        System.out.println("[VoxelAtlas] Getting handler for path: " + this.path + path);
        handlers.put(path, handler);

        this.handlers.put(HttpMethod.GET, handlers);
    }

    public void post(String path, IHttpRequestHandler handler) {
        final Map<String, IHttpRequestHandler> handlers = this.handlers.getOrDefault(HttpMethod.POST, new HashMap<>());
        System.out.println("[VoxelAtlas] Posting handler for path: " + this.path + path);
        handlers.put(path, handler);

        this.handlers.put(HttpMethod.POST, handlers);
    }

    public String path() {
        return this.path;
    }
}
