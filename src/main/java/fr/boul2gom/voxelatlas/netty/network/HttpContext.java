package fr.boul2gom.voxelatlas.netty.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.*;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Context wrapper for handling HTTP responses.
 * <p>
 * This record wraps the Netty {@link ChannelHandlerContext} and provides helper
 * methods
 * for sending various types of responses (text, HTML, JSON, binary files).
 * </p>
 * <p>
 * Constructor parameters:
 * <ul>
 * <li><b>ctx</b>: The Netty channel handler context used to write
 * responses.</li>
 * </ul>
 * </p>
 */
public record HttpContext(
    /** The Netty channel handler context */
    ChannelHandlerContext ctx
) {

    /**
     * Sends a binary response with explicit cache control.
     *
     * @param content       The byte array content to send.
     * @param content_type  The MIME type of the content.
     * @param status        The HTTP status code.
     * @param cache_control The Cache-Control header value (can be null).
     */
    public void send_binary(byte[] content, String content_type, HttpResponseStatus status, String cache_control) {
        final ByteBuf bytes = ByteBufAllocator.DEFAULT.buffer();
        bytes.writeBytes(content);

        final FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, bytes);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, content_type);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

        if (cache_control != null) {
            response.headers().set(HttpHeaderNames.CACHE_CONTROL, cache_control);
        }

        this.ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Sends a binary response without explicit cache control.
     *
     * @param content      The byte array content to send.
     * @param content_type The MIME type of the content.
     * @param status       The HTTP status code.
     */
    public void send_binary(byte[] content, String content_type, HttpResponseStatus status) {
        this.send_binary(content, content_type, status, null);
    }

    /**
     * Sends a text-based response with UTF-8 charset.
     *
     * @param content      The byte array content (assumed UTF-8).
     * @param content_type The MIME type (charset=UTF-8 will be appended).
     * @param status       The HTTP status code.
     */
    public void send_response(byte[] content, String content_type, HttpResponseStatus status) {
        final String with_charset = String.format("%s; charset=UTF-8", content_type);

        this.send_binary(content, with_charset, status);
    }

    /**
     * Sends a plain text response.
     *
     * @param content      The string content.
     * @param content_type The MIME type.
     * @param status       The HTTP status code.
     */
    public void text(String content, String content_type, HttpResponseStatus status) {
        this.send_response(content.getBytes(StandardCharsets.UTF_8), content_type, status);
    }

    /**
     * Sends a plain text response with "text/plain" content type.
     *
     * @param content The string content.
     * @param status  The HTTP status code.
     */
    public void text(String content, HttpResponseStatus status) {
        this.send_response(content.getBytes(StandardCharsets.UTF_8), HttpHeaderValues.TEXT_PLAIN.toString(), status);
    }

    /**
     * Sends a plain text response with status OK (200).
     *
     * @param content The string content.
     */
    public void text(String content) {
        this.text(content, HttpResponseStatus.OK);
    }

    /**
     * Sends an HTML response.
     *
     * @param html   The HTML string content.
     * @param status The HTTP status code.
     */
    public void html(String html, HttpResponseStatus status) {
        this.text(html, HttpHeaderValues.TEXT_HTML.toString(), status);
    }

    /**
     * Sends an HTML response with status OK (200).
     *
     * @param html The HTML string content.
     */
    public void html(String html) {
        this.html(html, HttpResponseStatus.OK);
    }

    /**
     * Sends a JSON error response.
     *
     * @param msg          The error message.
     * @param content_type The MIME type.
     * @param status       The HTTP status code.
     */
    private void error(String msg, String content_type, HttpResponseStatus status) {
        final HttpResponse response = new HttpResponse()
                .add("success", false)
                .add("code", status.code())
                .add("message", msg);

        this.send_response(response.toString().getBytes(StandardCharsets.UTF_8), content_type, status);
    }

    /**
     * Sends a standard JSON error response.
     *
     * @param msg    The error message.
     * @param status The HTTP status code.
     */
    public void error(String msg, HttpResponseStatus status) {
        this.error(msg, HttpHeaderValues.APPLICATION_JSON.toString(), status);
    }

    /**
     * Builds and sends a JSON response using a consumer.
     *
     * @param consumer The consumer to build the {@link HttpResponse}.
     * @param status   The HTTP status code.
     */
    public void json(Consumer<HttpResponse> consumer, HttpResponseStatus status) {
        final HttpResponse response = new HttpResponse().add("success", true).add("code", status.code());
        consumer.accept(response);

        this.text(response.toString(), "application/json", status);
    }

    /**
     * Builds and sends a JSON response with status OK (200).
     *
     * @param consumer The consumer to build the {@link HttpResponse}.
     */
    public void json(Consumer<HttpResponse> consumer) {
        this.json(consumer, HttpResponseStatus.OK);
    }

    /**
     * Sends a JSON response wrapping a single object.
     *
     * @param obj The object to wrap in the "response" field.
     */
    public void json(Object obj) {
        this.json(response -> response.add("response", obj), HttpResponseStatus.OK);
    }

    /**
     * Sends a PNG image response with caching enabled (24h).
     *
     * @param content The PNG byte array.
     */
    public void png(byte[] content) {
        this.send_binary(content, "image/png", HttpResponseStatus.OK, "public, max-age=86400");
    }

    /**
     * Sends a WebP image response with caching enabled (24h).
     *
     * @param content The WebP byte array.
     */
    public void webp(byte[] content) {
        this.send_binary(content, "image/webp", HttpResponseStatus.OK, "public, max-age=86400");
    }

    /**
     * Serves a file efficiently using Netty's Zero-Copy capability.
     *
     * @param file         The file to serve.
     * @param content_type The MIME type of the file.
     */
    public void send_file(File file, String content_type) {
        try (final RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            final long length = raf.length();

            final io.netty.handler.codec.http.HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, content_type);
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, length);
            response.headers().set(HttpHeaderNames.CACHE_CONTROL, "public, max-age=86400");

            this.ctx.write(response);
            final ChannelFuture send_future = this.ctx.write(
                    new DefaultFileRegion(raf.getChannel(), 0, length),
                    this.ctx.newProgressivePromise()
            );
            this.ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);

            send_future.addListener((ChannelFutureListener) _ -> raf.close());
        } catch (Exception e) {
            this.error("Failed to serve file", HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
