package fr.boul2gom.voxelaltas.netty.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class HttpContext {

    private final ChannelHandlerContext ctx;

    public HttpContext(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    public void send_binary(byte[] content, String contentType, HttpResponseStatus status) {
        final ByteBuf bytes = ByteBufAllocator.DEFAULT.buffer();
        bytes.writeBytes(content);

        final FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, bytes);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

        this.ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    public void send_response(byte[] content, String contentType, HttpResponseStatus status) {
        final String with_charset = String.format("%s; charset=UTF-8", contentType);

        this.send_binary(content, with_charset, status);
    }

    public void text(String content, String contentType, HttpResponseStatus status) {
        this.send_response(content.getBytes(StandardCharsets.UTF_8), contentType, status);
    }

    public void text(String content, HttpResponseStatus statusCode) {
        this.send_response(content.getBytes(StandardCharsets.UTF_8), HttpHeaderValues.TEXT_PLAIN.toString(), statusCode);
    }

    public void text(String content) {
        this.text(content, HttpResponseStatus.OK);
    }

    public void html(String html, HttpResponseStatus status) {
        this.text(html, HttpHeaderValues.TEXT_HTML.toString(), status);
    }

    public void html(String html) {
        this.html(html, HttpResponseStatus.OK);
    }

    private void error(String msg, String contentType, HttpResponseStatus status) {
        final HttpResponse response = new HttpResponse()
                .add("success", false)
                .add("code", status.code())
                .add("message", msg);

        this.send_response(response.toString().getBytes(StandardCharsets.UTF_8), contentType, status);
    }

    public void error(String msg, HttpResponseStatus status) {
        this.error(msg, HttpHeaderValues.APPLICATION_JSON.toString(), status);
    }

    public void json(Consumer<HttpResponse> consumer, HttpResponseStatus status) {
        final HttpResponse response = new HttpResponse().add("success", true).add("code", status.code());
        consumer.accept(response);

        this.text(response.toString(), "application/json", status);
    }

    public void json(Consumer<HttpResponse> consumer) {
        this.json(consumer, HttpResponseStatus.OK);
    }

    public void json(Object obj) {
        this.json(response -> response.add("response", obj), HttpResponseStatus.OK);
    }

    public void png(byte[] content) {
        this.send_binary(content, "image/png", HttpResponseStatus.OK);
    }

    public void webp(byte[] content) {
        this.send_binary(content, "image/webp", HttpResponseStatus.OK);
    }
}
