package fr.boul2gom.voxelatlas.netty.network.request;

import fr.boul2gom.voxelatlas.netty.network.HttpContext;

/**
 * Functional interface for handling HTTP requests.
 * <p>
 * Implementations of this interface process incoming {@link HttpRequest}s and
 * use the
 * provided {@link HttpContext} to send responses.
 * </p>
 */
@FunctionalInterface
public interface IHttpRequestHandler {

    /**
     * Handles an incoming HTTP request.
     *
     * @param request The {@link HttpRequest} object containing request details.
     * @param ctx     The {@link HttpContext} used to send the response.
     */
    void on_request(HttpRequest request, HttpContext ctx);
}
