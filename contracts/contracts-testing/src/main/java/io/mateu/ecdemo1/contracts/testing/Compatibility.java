package io.mateu.ecdemo1.contracts.testing;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What in a new schema a consumer of the old one might not read: a definition, property, variant or
 * enum value gone, a property's type changed, a property newly required. Everything else — a new
 * optional property, a new variant, a new definition — is additive.
 */
public final class Compatibility {

    private Compatibility() {
    }

    public static List<String> breakingChanges(JsonNode before, JsonNode after) {
        var changes = new ArrayList<String>();
        var oldVariants = refs(before.path("oneOf"));
        var newVariants = refs(after.path("oneOf"));
        oldVariants.stream().filter(v -> !newVariants.contains(v)).forEach(v -> changes.add("variant " + v + " removed"));
        before.path("$defs").fields().forEachRemaining(def -> {
            var name = def.getKey();
            var now = after.path("$defs").path(name);
            if (now.isMissingNode()) {
                changes.add("definition " + name + " removed");
                return;
            }
            def.getValue().path("properties").fields().forEachRemaining(property -> {
                var p = now.path("properties").path(property.getKey());
                if (p.isMissingNode()) {
                    changes.add(name + "." + property.getKey() + " removed");
                } else if (!type(property.getValue()).equals(type(p))) {
                    changes.add(name + "." + property.getKey() + " changed type: " + type(property.getValue()) + " → " + type(p));
                } else {
                    var oldEnum = strings(property.getValue().path("enum"));
                    var newEnum = strings(p.path("enum"));
                    oldEnum.stream().filter(v -> !newEnum.isEmpty() && !newEnum.contains(v))
                            .forEach(v -> changes.add(name + "." + property.getKey() + " no longer accepts " + v));
                }
            });
            var oldRequired = strings(def.getValue().path("required"));
            strings(now.path("required")).stream().filter(r -> !oldRequired.contains(r))
                    .forEach(r -> changes.add(name + "." + r + " is now required"));
            var oldEnum = strings(def.getValue().path("enum"));
            var newEnum = strings(now.path("enum"));
            oldEnum.stream().filter(v -> !newEnum.contains(v)).forEach(v -> changes.add(name + " no longer accepts " + v));
        });
        return changes;
    }

    static String type(JsonNode property) {
        if (property.has("$ref")) {
            return property.get("$ref").asText();
        }
        if (property.has("anyOf")) {
            var parts = new ArrayList<String>();
            property.get("anyOf").forEach(p -> parts.add(type(p)));
            parts.sort(null);
            return String.join("|", parts);
        }
        var type = property.path("type");
        var text = type.isArray() ? String.join("|", strings(type).stream().sorted().toList()) : type.asText();
        if (property.has("items")) {
            text += "<" + type(property.get("items")) + ">";
        }
        return text;
    }

    static Set<String> refs(JsonNode oneOf) {
        var result = new HashSet<String>();
        oneOf.forEach(v -> result.add(v.path("$ref").asText(v.toString())));
        return result;
    }

    static Set<String> strings(JsonNode array) {
        var result = new HashSet<String>();
        array.forEach(v -> result.add(v.asText()));
        return result;
    }
}
