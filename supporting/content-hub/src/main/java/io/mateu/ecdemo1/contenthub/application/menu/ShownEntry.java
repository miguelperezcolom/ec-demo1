package io.mateu.ecdemo1.contenthub.application.menu;

import java.util.List;

/**
 * A menu entry as one reader gets it: labelled in their language, with where it takes them, and only the
 * entries under it they may see — what a renderer draws.
 *
 * @param link null for a submenu
 */
public record ShownEntry(String id, String label, MenuLink link, List<ShownEntry> children) {

    public ShownEntry {
        children = children == null ? List.of() : List.copyOf(children);
    }

    public boolean isSubmenu() {
        return link == null;
    }
}
