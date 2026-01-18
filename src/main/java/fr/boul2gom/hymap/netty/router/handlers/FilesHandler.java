package fr.boul2gom.hymap.netty.router.handlers;

import fr.boul2gom.hymap.HytaleMap;
import fr.boul2gom.hymap.netty.router.HttpRouter;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Map;

public class FilesHandler {

    private static final Map<String, String> MIME_TYPES = Map.ofEntries(
            Map.entry("html", "text/html"),
            Map.entry("css", "text/css"),
            Map.entry("js", "application/javascript"),
            Map.entry("json", "application/json"),
            Map.entry("png", "image/png"),
            Map.entry("webp", "image/webp"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("woff2", "font/woff2")
    );

    public FilesHandler(HytaleMap plugin) {
        final HttpRouter main = plugin.netty().main_router();
        final HttpRouter router = main.child_router("/resources");

        router.get("/", (request, ctx) -> {
            try {
                if (!request.has_parameter("file")) {
                    ctx.error("Missing parameter 'file'", HttpResponseStatus.BAD_REQUEST);
                    return;
                }

                final String file = request.parameter("file").first_value();
                if (file.contains("..") || file.contains("\\")) {
                    ctx.error("Invalid filename", HttpResponseStatus.FORBIDDEN);
                    return;
                }

                final byte[] content = this.load_resource("/Server/" + file);
                if (content == null) {
                    ctx.error("File not found: " + file, HttpResponseStatus.NOT_FOUND);
                    return;
                }

                final String content_type = this.content_type(file);
                ctx.send_binary(content, content_type, HttpResponseStatus.OK);
            } catch (Exception e) {
                ctx.error("Invalid request!", HttpResponseStatus.BAD_REQUEST);
            }
        });
    }

    public byte[] load_resource(String path) throws URISyntaxException, IOException {
        try (InputStream is = this.getClass().getResourceAsStream(path)) {
            if (is == null) return null;
            return is.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private String content_type(String path) {
        final int dot_index = path.lastIndexOf('.');
        if (dot_index == -1 || dot_index == path.length() - 1) {
            return "application/octet-stream";
        }

        final String extension = path.substring(dot_index + 1).toLowerCase();

        return MIME_TYPES.getOrDefault(extension, "application/octet-stream");
    }
}
