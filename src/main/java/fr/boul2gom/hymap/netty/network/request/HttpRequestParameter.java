package fr.boul2gom.hymap.netty.network.request;

import java.util.List;

public class HttpRequestParameter {

    private final String key;
    private final List<String> values;

    public HttpRequestParameter(String key, List<String> values) {
        this.key = key;
        this.values = values;
    }

    public String key() {
        return this.key;
    }

    public List<String> values() {
        return this.values;
    }

    public String first_value() {
        return this.values.getFirst();
    }
}
