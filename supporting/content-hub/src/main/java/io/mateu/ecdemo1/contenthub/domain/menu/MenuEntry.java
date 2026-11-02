package io.mateu.ecdemo1.contenthub.domain.menu;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;

import java.util.Map;
import java.util.TreeMap;

/**
 * One entry of a site's menu: a link (it has a target) or a submenu (it has none, and holds entries).
 *
 * @param parentId null for a top-level entry; otherwise a submenu of the same menu
 * @param order    its position among its siblings, from 0
 * @param labels   its label per language (es, en, de…); the default language's is required
 * @param target   where it goes; null for a submenu
 * @param audience who sees it (null or {@link Audience#EVERYONE}: everyone) — the same model as items'
 * @param active   an inactive entry is kept, but nobody sees it (nor what hangs from it)
 */
public record MenuEntry(String id, String parentId, int order, Map<String, String> labels, MenuTarget target,
                        Audience audience, boolean active) {

    public MenuEntry {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Una entrada de menú necesita su id");
        }
        var clean = new TreeMap<String, String>();
        if (labels != null) {
            labels.forEach((language, label) -> {
                if (language != null && label != null && !label.isBlank()) {
                    clean.put(language.trim().toLowerCase(java.util.Locale.ROOT), label.trim());
                }
            });
        }
        labels = Map.copyOf(clean);
        audience = audience == null ? Audience.EVERYONE : audience;
    }

    public boolean isSubmenu() {
        return target == null;
    }

    /** Its label in a language: that language's, else the default language's, else any, else its id. */
    public String label(String language, String defaultLanguage) {
        var l = language == null ? null : labels.get(language);
        if (l != null) {
            return l;
        }
        l = labels.get(defaultLanguage);
        if (l != null) {
            return l;
        }
        return labels.values().stream().findFirst().orElse(id);
    }

    MenuEntry at(String newParentId, int newOrder) {
        return new MenuEntry(id, newParentId, newOrder, labels, target, audience, active);
    }
}
