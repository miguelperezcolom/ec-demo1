package io.mateu.ecdemo1.contenthub.application.menu;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.menu.MenuTarget;
import io.mateu.ecdemo1.contenthub.domain.menu.SiteMenu;

import java.util.Map;

/** The menus a site starts with. */
public final class DefaultMenus {

    private DefaultMenus() {
    }

    /**
     * The intranet's menu exactly as the portal drew it before menus were data: its functions, in their
     * order, with their labels, flat. Each entry's id is the function's key.
     */
    public static SiteMenu intranet() {
        var menu = SiteMenu.empty(SiteMenu.INTRANET);
        for (var f : PortalFunction.values()) {
            menu.add(f.key(), null, Map.of(SiteMenu.DEFAULT_LANGUAGE, f.label()), MenuTarget.builtin(f.key()),
                    Audience.EVERYONE, true);
        }
        return menu;
    }

    /** What a site has before anyone configures its menu: the intranet's, or nothing. */
    public static SiteMenu of(String siteId) {
        return SiteMenu.INTRANET.equals(siteId) ? intranet() : SiteMenu.empty(siteId);
    }
}
