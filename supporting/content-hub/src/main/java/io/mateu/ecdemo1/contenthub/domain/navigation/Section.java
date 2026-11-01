package io.mateu.ecdemo1.contenthub.domain.navigation;

import java.util.Map;
import java.util.TreeMap;

/**
 * A node of the navigation tree. No audience of its own: it shows when something in it, or below it,
 * is visible.
 *
 * @param parentId null for a top-level section
 * @param names    its name per language
 */
public record Section(String id, String parentId, int order, Map<String, String> names) {

    public Section {
        names = names == null ? Map.of() : Map.copyOf(new TreeMap<>(names));
    }

    public String name(String language) {
        var n = names.get(language);
        return n != null ? n : names.getOrDefault("es", names.values().stream().findFirst().orElse(id));
    }
}
