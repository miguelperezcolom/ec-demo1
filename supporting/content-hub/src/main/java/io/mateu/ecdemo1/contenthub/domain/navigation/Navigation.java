package io.mateu.ecdemo1.contenthub.domain.navigation;

import io.mateu.ecdemo1.contenthub.domain.shared.AggregateRoot;
import io.mateu.ecdemo1.contenthub.domain.shared.ContentEvents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The whole tree of sections, as one aggregate: moving or reordering a section changes its siblings, and
 * the tree changes seldom, so keeping it whole costs no contention. Invariants: no cycles, and siblings
 * in a single order.
 */
public class Navigation extends AggregateRoot {

    private List<Section> sections;

    @SuppressWarnings("unused") // for the store
    private Navigation() {
    }

    public static Navigation empty() {
        var n = new Navigation();
        n.sections = new ArrayList<>();
        return n;
    }

    public List<Section> sections() {
        return sections.stream().sorted(Comparator.comparing((Section s) -> depth(s.id())).thenComparing(Section::order))
                .toList();
    }

    public Optional<Section> find(String id) {
        return sections.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    public List<Section> children(String parentId) {
        return sections.stream().filter(s -> Objects.equals(s.parentId(), parentId))
                .sorted(Comparator.comparingInt(Section::order)).toList();
    }

    public void add(String id, String parentId, Map<String, String> names) {
        if (find(id).isPresent()) {
            throw new IllegalArgumentException("Ya hay una sección " + id);
        }
        requireParent(parentId);
        sections.add(new Section(id, parentId, children(parentId).size(), names));
        raise(new ContentEvents.NavigationChanged());
    }

    public void rename(String id, Map<String, String> names) {
        var s = find(id).orElseThrow();
        replace(new Section(s.id(), s.parentId(), s.order(), names));
        raise(new ContentEvents.NavigationChanged());
    }

    /** Moves a section under another (or to the top, with null) at a position among its new siblings. */
    public void move(String id, String newParentId, int position) {
        var s = find(id).orElseThrow();
        requireParent(newParentId);
        if (id.equals(newParentId) || (newParentId != null && ancestors(newParentId).contains(id))) {
            throw new IllegalArgumentException("Una sección no puede quedar dentro de sí misma");
        }
        var oldParent = s.parentId();
        sections.remove(s);
        renumber(oldParent);
        var siblings = new ArrayList<>(children(newParentId));
        var at = Math.max(0, Math.min(position, siblings.size()));
        siblings.add(at, new Section(s.id(), newParentId, at, s.names()));
        for (int i = 0; i < siblings.size(); i++) {
            var sib = siblings.get(i);
            sections.removeIf(x -> x.id().equals(sib.id()));
            sections.add(new Section(sib.id(), sib.parentId(), i, sib.names()));
        }
        raise(new ContentEvents.NavigationChanged());
    }

    public void remove(String id) {
        if (!children(id).isEmpty()) {
            throw new IllegalStateException("La sección tiene subsecciones");
        }
        var s = find(id).orElseThrow();
        sections.remove(s);
        renumber(s.parentId());
        raise(new ContentEvents.NavigationChanged());
    }

    /** The section's ancestors, nearest first. */
    public List<String> ancestors(String id) {
        var out = new ArrayList<String>();
        var seen = new HashSet<String>();
        var current = find(id).map(Section::parentId).orElse(null);
        while (current != null && seen.add(current)) {
            out.add(current);
            current = find(current).map(Section::parentId).orElse(null);
        }
        return out;
    }

    /**
     * The sections to show, given those that directly contain something visible: each of them and all
     * their ancestors. A section with nothing visible in it or below it disappears.
     */
    public Set<String> shown(Collection<String> sectionsWithVisibleContent) {
        var out = new LinkedHashSet<String>();
        for (var id : sectionsWithVisibleContent) {
            if (id != null && find(id).isPresent()) {
                out.add(id);
                out.addAll(ancestors(id));
            }
        }
        return out;
    }

    private int depth(String id) {
        return ancestors(id).size();
    }

    private void requireParent(String parentId) {
        if (parentId != null && find(parentId).isEmpty()) {
            throw new IllegalArgumentException("No hay sección " + parentId);
        }
    }

    private void replace(Section section) {
        sections.removeIf(x -> x.id().equals(section.id()));
        sections.add(section);
    }

    private void renumber(String parentId) {
        var siblings = children(parentId);
        for (int i = 0; i < siblings.size(); i++) {
            var sib = siblings.get(i);
            replace(new Section(sib.id(), sib.parentId(), i, sib.names()));
        }
    }
}
