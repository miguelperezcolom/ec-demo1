package io.mateu.ecdemo1.apimcp.spec;

import io.swagger.v3.oas.models.media.Schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns an OpenAPI schema into the JSON Schema a model is shown.
 *
 * <p>Hand-written rather than serialising the Swagger model, because those objects carry fields
 * that are not JSON Schema — {@code exampleSetFlag} and friends — and a tool schema with noise in
 * it is a tool description with noise in it. Only what a model needs to fill the thing in
 * survives: type, description, enum, item and property shapes, and required.
 *
 * <p>Depth-capped. A spec is a document from somewhere else, and a self-referential schema that
 * survived {@code resolveFully} would otherwise be a stack overflow at startup rather than a tool
 * that is merely less precisely described than it could be.
 */
final class SchemaMapper {

    /** Deep enough for the shapes real APIs use, shallow enough that a cycle cannot run away. */
    private static final int MAX_DEPTH = 12;

    private SchemaMapper() {
    }

    static Map<String, Object> toJsonSchema(Schema<?> schema) {
        return convert(schema, 0);
    }

    private static Map<String, Object> convert(Schema<?> schema, int depth) {
        var out = new LinkedHashMap<String, Object>();
        if (schema == null || depth > MAX_DEPTH) {
            // An untyped object rather than nothing: the model can still pass something through,
            // and the alternative is a property that cannot be filled in at all.
            out.put("type", "object");
            return out;
        }
        var type = schema.getType();
        if (type != null) {
            out.put("type", type);
        } else if (schema.getProperties() != null) {
            // A schema with properties and no declared type is an object everywhere but in the
            // letter of the document, and models read the type.
            out.put("type", "object");
        }
        if (schema.getFormat() != null) {
            out.put("format", schema.getFormat());
        }
        if (schema.getDescription() != null) {
            out.put("description", schema.getDescription());
        }
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            out.put("enum", new ArrayList<Object>(schema.getEnum()));
        }
        if (schema.getItems() != null) {
            out.put("items", convert(schema.getItems(), depth + 1));
        }
        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            var properties = new LinkedHashMap<String, Object>();
            schema.getProperties().forEach((name, property) ->
                    properties.put(name, convert(property, depth + 1)));
            out.put("properties", properties);
        }
        if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
            out.put("required", List.copyOf(schema.getRequired()));
        }
        return out;
    }
}
