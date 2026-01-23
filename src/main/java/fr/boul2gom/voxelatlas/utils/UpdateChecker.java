package fr.boul2gom.voxelatlas.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.common.semver.Semver;
import fr.boul2gom.voxelatlas.VoxelAtlas;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

/**
 * Utility class to check for plugin updates on GitHub.
 * <p>
 * This class asynchronously queries the GitHub API to fetch the latest release
 * tag
 * and compares it with the current plugin version.
 * </p>
 */
public class UpdateChecker {

    /** The GitHub API endpoint for the latest release */
    private static final String REPO_URL = "https://api.github.com/repos/boul2gom/VoxelAtlas/releases/latest";
    /** The current version of the plugin */
    private final Semver version;

    /**
     * Create a new update checker.
     *
     * @param plugin The VoxelAtlas plugin instance.
     */
    public UpdateChecker(VoxelAtlas plugin) {
        this.version = plugin.getManifest().getVersion();
    }

    /**
     * Performs an asynchronous check for updates.
     * <p>
     * If a new version is found, a warning message is logged with the download
     * link.
     * </p>
     */
    public void check() {
        CompletableFuture.runAsync(() -> {
            try {
                final URL url = URI.create(REPO_URL).toURL();
                final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "VoxelAtlas");
                connection.setRequestProperty("Accept", "application/vnd.github+json");

                if (connection.getResponseCode() == 200) {
                    try (final InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                        final JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        final String latest_tag = json.get("tag_name").getAsString();

                        // Remove 'v' prefix if present
                        final String latest_version_str = latest_tag.startsWith("v") ? latest_tag.substring(1)
                                : latest_tag;

                        try {
                            final Semver latest_version = Semver.fromString(latest_version_str);

                            if (latest_version.compareTo(version) > 0) {
                                VoxelAtlas.LOGGER.atWarning()
                                        .log("[VoxelAtlas] A new version is available: " + latest_tag);
                                VoxelAtlas.LOGGER.atWarning().log("[VoxelAtlas] You are running version: " + version);
                                VoxelAtlas.LOGGER.atWarning()
                                        .log("[VoxelAtlas] Download it at: " + json.get("html_url").getAsString());
                            }
                        } catch (Exception e) {
                            VoxelAtlas.LOGGER.atWarning()
                                    .log("[VoxelAtlas] Failed to parse latest version: " + latest_version_str);
                        }
                    }
                } else {
                    VoxelAtlas.LOGGER.atWarning().log(
                            "[VoxelAtlas] Failed to check for updates. Response code: " + connection.getResponseCode());
                }
            } catch (Exception e) {
                VoxelAtlas.LOGGER.atWarning().log("[VoxelAtlas] Failed to check for updates: " + e.getMessage());
            }
        });
    }
}
