package fr.boul2gom.voxelatlas.netty.router.handlers;

import fr.boul2gom.voxelatlas.VoxelAtlas;
import fr.boul2gom.voxelatlas.netty.router.HttpRouter;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Handles HTTP requests for static file resources.
 * <p>
 * This handler serves files from the classpath (specifically under /Server/).
 * It validates filenames to prevent path traversal attacks and sets appropriate
 * content types.
 * </p>
 */
public class FilesHandler {

    /** Map of file extensions to their corresponding MIME types */
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

    /**
     * Registers the file handler routes.
     * <p>
     * Sets up a GET route at "/resources" to serve static files.
     * </p>
     *
     * @param plugin The VoxelAtlas plugin instance.
     */
    public FilesHandler(VoxelAtlas plugin) {
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

    /**
     * Loads a resource file from the classpath.
     *
     * @param path The absolute path to the resource on the classpath.
     * @return The file content as a byte array, or null if not found/error.
     */
    public byte[] load_resource(String path) {
        try (final InputStream is = this.getClass().getResourceAsStream(path)) {
            if (is == null) return null;
            return is.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Determines the MIME type based on the file extension.
     *
     * @param path The file path or name.
     * @return The MIME type string, or "application/octet-stream" if unknown.
     */
    private String content_type(String path) {
        final int dot_index = path.lastIndexOf('.');
        if (dot_index == -1 || dot_index == path.length() - 1) {
            return "application/octet-stream";
        }

        final String extension = path.substring(dot_index + 1).toLowerCase();
        return MIME_TYPES.getOrDefault(extension, "application/octet-stream");
    }
}
