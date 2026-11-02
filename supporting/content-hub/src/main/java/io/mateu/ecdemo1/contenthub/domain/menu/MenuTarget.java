package io.mateu.ecdemo1.contenthub.domain.menu;

import java.util.Locale;

/**
 * Where a menu entry goes. The kind is an open code, not an enum: PAGE, SECTION, BUILTIN and URL are
 * resolved by the content-hub itself, and a site type may add its own kinds (a newsletter's archive or
 * issue, say) by registering a resolver for them ({@code application.menu.MenuTargetResolver}).
 *
 * @param kind  what the value names — {@link #PAGE}, {@link #SECTION}, {@link #BUILTIN}, {@link #URL}, or
 *              an extension's code (upper case)
 * @param value the page id, the section id, the portal function's key, the path or URL…
 */
public record MenuTarget(String kind, String value) {

    /** A content-hub item of type PAGE, by id: its portal route. */
    public static final String PAGE = "PAGE";
    /** A section of the navigation tree, by id: its listing. */
    public static final String SECTION = "SECTION";
    /** One of the portal's own functions (noticias, buscar, por aprobar…), by key. */
    public static final String BUILTIN = "BUILTIN";
    /** A path inside the portal (/intranet/…) or an external http(s) URL. */
    public static final String URL = "URL";
    /**
     * Another UI, from the registry of UI sources: the value is {@code sourceId} or
     * {@code sourceId/route/inside/it} (see {@link #remote}).
     */
    public static final String REMOTE = "REMOTE";

    public MenuTarget {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("Un destino necesita su tipo");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta el destino (" + kind + ")");
        }
        kind = kind.trim().toUpperCase(Locale.ROOT);
        value = value.trim();
        if (URL.equals(kind) && !isExternal(value) && !value.startsWith("/")) {
            throw new IllegalArgumentException("Un enlace es una ruta del portal (/intranet/…) o una URL http(s)");
        }
        if (REMOTE.equals(kind) && value.startsWith("/")) {
            throw new IllegalArgumentException("Falta la interfaz (su id) antes de la ruta");
        }
    }

    /** Another UI: a source of the registry and, optionally, a route inside it ("/users/42"). */
    public static MenuTarget remote(String sourceId, String route) {
        var r = route == null || route.isBlank() || "/".equals(route.trim()) ? ""
                : route.trim().startsWith("/") ? route.trim() : "/" + route.trim();
        return new MenuTarget(REMOTE, (sourceId == null ? "" : sourceId.trim()) + r);
    }

    /** For a REMOTE target: the source's id. */
    public String remoteSource() {
        var slash = value.indexOf('/');
        return slash < 0 ? value : value.substring(0, slash);
    }

    /** For a REMOTE target: the route inside the source ("" for its home). */
    public String remoteRoute() {
        var slash = value.indexOf('/');
        return slash < 0 ? "" : value.substring(slash);
    }

    public static MenuTarget page(String pageId) {
        return new MenuTarget(PAGE, pageId);
    }

    public static MenuTarget section(String sectionId) {
        return new MenuTarget(SECTION, sectionId);
    }

    public static MenuTarget builtin(String key) {
        return new MenuTarget(BUILTIN, key);
    }

    public static MenuTarget url(String url) {
        return new MenuTarget(URL, url);
    }

    /** Whether a URL leaves the portal: http(s), opened in a new tab. */
    public static boolean isExternal(String url) {
        var u = url == null ? "" : url.trim().toLowerCase(Locale.ROOT);
        return u.startsWith("http://") || u.startsWith("https://");
    }
}
