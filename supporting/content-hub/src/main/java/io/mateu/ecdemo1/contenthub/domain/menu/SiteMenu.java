package io.mateu.ecdemo1.contenthub.domain.menu;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A site's menu, whole, as one aggregate — like the sections' tree: reordering an entry changes its
 * siblings, and a menu changes seldom. The owner is a site id: {@value #INTRANET} for the intranet
 * portal, and each other site (a newsletter) its own.
 *
 * <p>An ordered tree: entries are links (with a target) or submenus (without one, holding entries); a
 * submenu may hold submenus. Invariants: every entry hangs from the root or from a submenu of the same
 * menu, no cycles, siblings numbered 0..n-1, and every entry is labelled in the default language.
 *
 * <p>The menu decides what is shown, in which order and with which labels — not the routes: a link's
 * route is its target's, so deep links work whatever the menu holds.
 */
public class SiteMenu {

    /** The intranet portal's site id. */
    public static final String INTRANET = "intranet";
    public static final String DEFAULT_LANGUAGE = "es";

    private String siteId;
    private String defaultLanguage;
    private List<MenuEntry> entries;

    @SuppressWarnings("unused") // for the store
    private SiteMenu() {
    }

    public static SiteMenu empty(String siteId) {
        return empty(siteId, DEFAULT_LANGUAGE);
    }

    public static SiteMenu empty(String siteId, String defaultLanguage) {
        if (siteId == null || siteId.isBlank()) {
            throw new IllegalArgumentException("Un menú pertenece a un sitio");
        }
        var m = new SiteMenu();
        m.siteId = siteId.trim();
        m.defaultLanguage = defaultLanguage == null || defaultLanguage.isBlank() ? DEFAULT_LANGUAGE : defaultLanguage;
        m.entries = new ArrayList<>();
        return m;
    }

    public String siteId() {
        return siteId;
    }

    public String defaultLanguage() {
        return defaultLanguage == null ? DEFAULT_LANGUAGE : defaultLanguage;
    }

    /** Every entry, depth-first in menu order: each submenu followed by what it holds. */
    public List<MenuEntry> entries() {
        var out = new ArrayList<MenuEntry>();
        collect(null, out, new HashSet<>());
        return out;
    }

    private void collect(String parentId, List<MenuEntry> out, java.util.Set<String> seen) {
        for (var e : children(parentId)) {
            if (seen.add(e.id())) {
                out.add(e);
                collect(e.id(), out, seen);
            }
        }
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public Optional<MenuEntry> find(String id) {
        return entries.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    public List<MenuEntry> children(String parentId) {
        return entries.stream().filter(e -> Objects.equals(e.parentId(), parentId))
                .sorted(Comparator.comparingInt(MenuEntry::order)).toList();
    }

    /** How deep an entry is: 0 at the top. */
    public int depth(String id) {
        return ancestors(id).size();
    }

    /** The entry's ancestors, nearest first. */
    public List<String> ancestors(String id) {
        var out = new ArrayList<String>();
        var seen = new HashSet<String>();
        var current = find(id).map(MenuEntry::parentId).orElse(null);
        while (current != null && seen.add(current)) {
            out.add(current);
            current = find(current).map(MenuEntry::parentId).orElse(null);
        }
        return out;
    }

    /** Adds an entry at the end of its parent's (null: the top level). */
    public void add(String id, String parentId, Map<String, String> labels, MenuTarget target, Audience audience,
                    boolean active) {
        if (find(id).isPresent()) {
            throw new IllegalArgumentException("Ya hay una entrada " + id + " en el menú de " + siteId);
        }
        requireSubmenu(parentId);
        var entry = new MenuEntry(id, parentId, children(parentId).size(), labels, target, audience, active);
        requireDefaultLabel(entry);
        entries.add(entry);
    }

    /** Changes what an entry says, where it goes, who sees it and whether it is active — not where it is. */
    public void update(String id, Map<String, String> labels, MenuTarget target, Audience audience, boolean active) {
        var e = require(id);
        if (target != null && !children(id).isEmpty()) {
            throw new IllegalStateException("«" + e.label(defaultLanguage(), defaultLanguage())
                    + "» tiene entradas: sigue siendo un submenú (sin destino)");
        }
        var updated = new MenuEntry(id, e.parentId(), e.order(), labels, target, audience, active);
        requireDefaultLabel(updated);
        replace(updated);
    }

    /** Moves an entry under a submenu (or to the top, with null) at a position among its new siblings. */
    public void move(String id, String newParentId, int position) {
        var e = require(id);
        requireSubmenu(newParentId);
        if (id.equals(newParentId) || (newParentId != null && ancestors(newParentId).contains(id))) {
            throw new IllegalArgumentException("Una entrada no puede quedar dentro de sí misma");
        }
        var oldParent = e.parentId();
        entries.remove(e);
        renumber(oldParent);
        var siblings = new ArrayList<>(children(newParentId));
        var at = Math.max(0, Math.min(position, siblings.size()));
        siblings.add(at, e.at(newParentId, at));
        for (int i = 0; i < siblings.size(); i++) {
            replace(siblings.get(i).at(newParentId, i));
        }
    }

    public void moveUp(String id) {
        var e = require(id);
        move(id, e.parentId(), e.order() - 1);
    }

    public void moveDown(String id) {
        var e = require(id);
        move(id, e.parentId(), e.order() + 1);
    }

    /** Into the submenu just above it, at its end. */
    public void indent(String id) {
        var e = require(id);
        if (e.order() == 0) {
            throw new IllegalStateException("No hay un submenú encima donde meterla");
        }
        var above = children(e.parentId()).get(e.order() - 1);
        if (!above.isSubmenu()) {
            throw new IllegalStateException("La entrada de encima no es un submenú");
        }
        move(id, above.id(), children(above.id()).size());
    }

    /** Out of its submenu, right after it. */
    public void outdent(String id) {
        var e = require(id);
        if (e.parentId() == null) {
            throw new IllegalStateException("Ya está en el primer nivel");
        }
        var parent = require(e.parentId());
        move(id, parent.parentId(), parent.order() + 1);
    }

    /** Removes an entry and, for a submenu, everything in it. */
    public void remove(String id) {
        var e = require(id);
        for (var child : children(id)) {
            remove(child.id());
        }
        entries.removeIf(x -> x.id().equals(id));
        renumber(e.parentId());
    }

    /**
     * What a reader is shown: the active entries the predicate lets through, in order, labelled in their
     * language. An entry not shown takes what hangs from it along; a submenu left with nothing shown in
     * it is dropped too.
     *
     * @param shows whether the reader may see an entry — its audience and its target (a page they cannot
     *              see, a function they cannot use) — asked only of active entries
     */
    public List<MenuNode> visible(Predicate<MenuEntry> shows, String language) {
        return visible(null, shows, language, new HashSet<>());
    }

    private List<MenuNode> visible(String parentId, Predicate<MenuEntry> shows, String language,
                                   java.util.Set<String> seen) {
        var out = new ArrayList<MenuNode>();
        for (var e : children(parentId)) {
            if (!seen.add(e.id()) || !e.active() || !shows.test(e)) {
                continue;
            }
            var label = e.label(language, defaultLanguage());
            if (e.isSubmenu()) {
                var children = visible(e.id(), shows, language, seen);
                if (!children.isEmpty()) {
                    out.add(new MenuNode(e.id(), label, null, children));
                }
            } else {
                out.add(new MenuNode(e.id(), label, e.target(), List.of()));
            }
        }
        return out;
    }

    private MenuEntry require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("No hay entrada " + id + " en el menú de " + siteId));
    }

    private void requireSubmenu(String parentId) {
        if (parentId == null) {
            return;
        }
        var parent = require(parentId);
        if (!parent.isSubmenu()) {
            throw new IllegalArgumentException("«" + parent.label(defaultLanguage(), defaultLanguage())
                    + "» es un enlace, no un submenú");
        }
    }

    private void requireDefaultLabel(MenuEntry e) {
        if (!e.labels().containsKey(defaultLanguage())) {
            throw new IllegalArgumentException("Falta el texto en " + defaultLanguage());
        }
    }

    private void replace(MenuEntry entry) {
        entries.removeIf(x -> x.id().equals(entry.id()));
        entries.add(entry);
    }

    private void renumber(String parentId) {
        var siblings = children(parentId);
        for (int i = 0; i < siblings.size(); i++) {
            replace(siblings.get(i).at(parentId, i));
        }
    }
}
