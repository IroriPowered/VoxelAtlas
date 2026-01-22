package fr.boul2gom.voxelatlas.dynmap.renderer;

/**
 * Enumeration of available renderer types.
 * <p>
 * This enum defines the different rendering strategies available for map tiles.
 * </p>
 */
public enum RendererType {
    /** Detailed flat 2D renderer */
    FLAT("flat");

    /** The unique identifier for the renderer type */
    private final String id;

    /**
     * Create a new renderer type.
     *
     * @param id The unique identifier string for this renderer type.
     */
    RendererType(String id) {
        this.id = id;
    }

    /**
     * Gets the unique identifier for this renderer type.
     *
     * @return The identifier string.
     */
    public String id() {
        return this.id;
    }

    /**
     * Resolves a renderer type from its identifier string.
     *
     * @param id The identifier string to look up.
     * @return The matching {@link RendererType}, or defaults to
     *         {@link RendererType#FLAT} if unknown.
     */
    public static RendererType from_id(String id) {
        return FLAT;
    }
}
