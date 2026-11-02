package io.mateu.ecdemo1.contenthub.domain.menu;

import java.util.List;

/**
 * An entry as one reader sees it: labelled in their language, with only what they may see under it.
 *
 * @param target null for a submenu
 */
public record MenuNode(String id, String label, MenuTarget target, List<MenuNode> children) {

    public MenuNode {
        children = children == null ? List.of() : List.copyOf(children);
    }

    public boolean isSubmenu() {
        return target == null;
    }
}
