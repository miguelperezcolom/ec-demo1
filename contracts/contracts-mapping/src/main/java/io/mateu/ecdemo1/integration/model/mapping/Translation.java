package io.mateu.ecdemo1.integration.model.mapping;

import java.util.Map;

/**
 * What a CRS code is in the PMS. Not always one code: {@code attributes} carries what else the
 * target needs — a channel is a source code and a market code in Opera.
 */
public record Translation(CodeType type, String sourceCode, String targetCode, Map<String, String> attributes) {

    public String attribute(String name) {
        return attributes == null ? null : attributes.get(name);
    }
}
