package io.mateu.ecdemo1.integrations.worker.runtime;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The mapper the worker runtime binds variables with, reading a scalar variable as the string it is.
 *
 * <p>The runtime reads every variable value that parses as JSON as that JSON: right for an object or
 * an array, wrong for an id that happens to look like a number — a locator {@code 12E45} would reach
 * the handler as {@code 1.2E46}, a {@code 1.50} as {@code 1.5}. Here only objects and arrays are
 * parsed; every scalar stays text, which Jackson still coerces into a number or a boolean field.
 */
public final class ExactStrings extends ObjectMapper {

    public ExactStrings(ObjectMapper source) {
        super(source);
    }

    @Override
    public JsonNode readTree(String content) throws JsonProcessingException {
        var trimmed = content == null ? "" : content.stripLeading();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return super.readTree(content);
        }
        throw new JsonParseException(null, "a scalar variable is read as text");
    }

    @Override
    public ObjectMapper copy() {
        return new ExactStrings(this);
    }
}
