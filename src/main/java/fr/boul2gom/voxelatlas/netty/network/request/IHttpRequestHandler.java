package fr.boul2gom.voxelatlas.netty.network.request;

import fr.boul2gom.voxelatlas.netty.network.HttpContext;

@FunctionalInterface
public interface IHttpRequestHandler {

    void on_request(HttpRequest request, HttpContext ctx);
}
