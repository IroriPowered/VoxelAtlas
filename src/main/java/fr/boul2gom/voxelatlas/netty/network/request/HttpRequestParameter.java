package fr.boul2gom.voxelatlas.netty.network.request;

import java.util.List;

/**
 * Represents a parsed HTTP request parameter.
 * <p>
 * A parameter can have multiple values (e.g., ?key=value1&key=value2).
 * </p>
 * <p>
 * Constructor parameters:
 * <ul>
 * <li><b>key</b>: The parameter key.</li>
 * <li><b>values</b>: The list of values associated with this key.</li>
 * </ul>
 * </p>
 */
public record HttpRequestParameter(
    /** The parameter key */
    String key,
    /** The list of values associated with this key */
    List<String> values
) {

    /**
     * Gets the first value of this parameter.
     *
     * @return The first value string.
     */
    public String first_value() {
        return this.values.getFirst();
    }
}
