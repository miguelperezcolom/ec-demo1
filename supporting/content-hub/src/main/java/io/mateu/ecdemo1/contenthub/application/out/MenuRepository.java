package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.menu.SiteMenu;

import java.util.List;
import java.util.Optional;

/** The sites' menus: one document per site. */
public interface MenuRepository {

    Optional<SiteMenu> find(String siteId);

    void save(SiteMenu menu);

    /**
     * Stores the menu only if its site has none yet — the seed, safe to run on every boot and from
     * several replicas at once.
     *
     * @return whether it was stored
     */
    boolean saveIfAbsent(SiteMenu menu);

    /** The sites that have a menu stored. */
    List<String> siteIds();
}
