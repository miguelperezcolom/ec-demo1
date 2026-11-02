package io.mateu.ecdemo1.contenthub.application.menu;

import io.mateu.ecdemo1.contenthub.application.out.MenuRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.menu.MenuEntry;
import io.mateu.ecdemo1.contenthub.domain.menu.MenuNode;
import io.mateu.ecdemo1.contenthub.domain.menu.MenuTarget;
import io.mateu.ecdemo1.contenthub.domain.menu.SiteMenu;
import io.mateu.ecdemo1.contenthub.domain.visibility.VisibilityPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The sites' menus: configured as data (by content:admin) and drawn per reader. A site is an id —
 * {@link SiteMenu#INTRANET} for the intranet portal, a newsletter's own for each newsletter — and every
 * operation here takes it, so any site type reuses this piece as it is.
 *
 * <p>Drawing ({@link #shown}) is pure in the menu, the reader and the targets: an entry is shown when it
 * is active, its audience admits the reader (the same policy as the content's) and its target resolves
 * for them; a submenu left empty is dropped.
 */
@Service
public class SiteMenus {

    final MenuRepository menus;
    final NamedAudiences namedAudiences;
    final Map<String, MenuTargetResolver> resolvers = new LinkedHashMap<>();

    public SiteMenus(MenuRepository menus, NamedAudiences namedAudiences, List<MenuTargetResolver> resolvers) {
        this.menus = menus;
        this.namedAudiences = namedAudiences;
        resolvers.forEach(r -> this.resolvers.put(r.kind(), r));
    }

    // ── reading ─────────────────────────────────────────────────────────────

    /** The site's menu: as configured, or the one it starts with (the intranet's current menu, or none). */
    public SiteMenu menu(String siteId) {
        return menus.find(siteId).orElseGet(() -> DefaultMenus.of(siteId));
    }

    /** The sites with a menu: the intranet always, and every site whose menu has been stored. */
    public List<String> sites() {
        var out = new LinkedHashSet<String>();
        out.add(SiteMenu.INTRANET);
        out.addAll(menus.siteIds());
        return List.copyOf(out);
    }

    /** The target kinds that apply to a site, for the configuration's select. */
    public List<MenuTargetResolver> targetKinds(String siteId) {
        return resolvers.values().stream().filter(r -> r.appliesTo(siteId)).toList();
    }

    public Optional<MenuTargetResolver> targetKind(String kind) {
        return Optional.ofNullable(kind == null ? null : resolvers.get(kind));
    }

    /** A target in words: «Función del portal: Noticias». */
    public String describe(String siteId, MenuTarget target) {
        if (target == null) {
            return "Submenú";
        }
        var r = resolvers.get(target.kind());
        return r == null ? target.kind() + ": " + target.value() + " (tipo desconocido)"
                : r.label() + ": " + r.describe(siteId, target);
    }

    // ── drawing ─────────────────────────────────────────────────────────────

    /** The menu this reader gets on the context's site. */
    public List<ShownEntry> shown(MenuContext context) {
        var menu = menu(context.siteId());
        VisibilityPolicy policy = context.memo("audiences.policy", namedAudiences::policy);
        var attributes = context.reader().attributes();
        var links = new HashMap<String, Optional<MenuLink>>();
        List<MenuNode> nodes = menu.visible(e -> policy.admits(e.audience(), attributes)
                && (e.isSubmenu() || links.computeIfAbsent(e.id(), id -> resolve(context, e.target())).isPresent()),
                context.language());
        return toShown(nodes, links);
    }

    /** Where one entry takes this reader, if they get it — for the pages an entry opens (a new tab, a frame). */
    public Optional<ShownEntry> shownEntry(MenuContext context, String entryId) {
        return find(shown(context), entryId);
    }

    private Optional<MenuLink> resolve(MenuContext context, MenuTarget target) {
        var r = resolvers.get(target.kind());
        if (r == null || !r.appliesTo(context.siteId())) {
            return Optional.empty();
        }
        return r.resolve(context, target);
    }

    private static List<ShownEntry> toShown(List<MenuNode> nodes, Map<String, Optional<MenuLink>> links) {
        var out = new ArrayList<ShownEntry>();
        for (var n : nodes) {
            out.add(new ShownEntry(n.id(), n.label(), n.isSubmenu() ? null : links.get(n.id()).orElseThrow(),
                    toShown(n.children(), links)));
        }
        return out;
    }

    private static Optional<ShownEntry> find(List<ShownEntry> entries, String id) {
        for (var e : entries) {
            if (e.id().equals(id)) {
                return Optional.of(e);
            }
            var inside = find(e.children(), id);
            if (inside.isPresent()) {
                return inside;
            }
        }
        return Optional.empty();
    }

    // ── configuring (content:admin) ─────────────────────────────────────────

    /** Adds an entry at the end of its submenu (or of the top level, with a blank parent); answers its id. */
    @Transactional
    public String add(Reader reader, String siteId, String parentId, Map<String, String> labels, MenuTarget target,
                      Audience audience, boolean active) {
        var id = "m" + UUID.randomUUID().toString().substring(0, 8);
        change(reader, siteId, m -> m.add(id, blankToNull(parentId), labels, requireKnown(target), audience, active));
        return id;
    }

    @Transactional
    public void update(Reader reader, String siteId, String entryId, Map<String, String> labels, MenuTarget target,
                       Audience audience, boolean active) {
        change(reader, siteId, m -> m.update(entryId, labels, requireKnown(target), audience, active));
    }

    @Transactional
    public void move(Reader reader, String siteId, String entryId, String parentId, int position) {
        change(reader, siteId, m -> m.move(entryId, blankToNull(parentId), position));
    }

    @Transactional
    public void moveUp(Reader reader, String siteId, String entryId) {
        change(reader, siteId, m -> m.moveUp(entryId));
    }

    @Transactional
    public void moveDown(Reader reader, String siteId, String entryId) {
        change(reader, siteId, m -> m.moveDown(entryId));
    }

    @Transactional
    public void indent(Reader reader, String siteId, String entryId) {
        change(reader, siteId, m -> m.indent(entryId));
    }

    @Transactional
    public void outdent(Reader reader, String siteId, String entryId) {
        change(reader, siteId, m -> m.outdent(entryId));
    }

    /** Removes an entry and, for a submenu, everything in it. */
    @Transactional
    public void remove(Reader reader, String siteId, String entryId) {
        change(reader, siteId, m -> m.remove(entryId));
    }

    /** Back to the menu the site starts with (for the intranet: the portal's functions, as always). */
    @Transactional
    public void reset(Reader reader, String siteId) {
        reader.require(EditorialPermission.ADMIN);
        menus.save(DefaultMenus.of(siteId));
    }

    /**
     * Stores a site's first menu if it has none — the seed; idempotent, and harmless from several replicas.
     * No permission: it is the system's, at start-up (or a site type's, when it creates a site).
     */
    public boolean seedIfAbsent(SiteMenu menu) {
        return menus.saveIfAbsent(menu);
    }

    private void change(Reader reader, String siteId, Consumer<SiteMenu> operation) {
        reader.require(EditorialPermission.ADMIN);
        var menu = menu(siteId);
        operation.accept(menu);
        menus.save(menu);
    }

    private MenuTarget requireKnown(MenuTarget target) {
        if (target != null && !resolvers.containsKey(target.kind())) {
            throw new IllegalArgumentException("Tipo de destino desconocido: " + target.kind());
        }
        return target;
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** For the listing: an entry and how deep it is, in menu order. */
    public record Line(MenuEntry entry, int depth) {
    }

    public List<Line> lines(String siteId) {
        var menu = menu(siteId);
        return menu.entries().stream().map(e -> new Line(e, menu.depth(e.id()))).toList();
    }
}
