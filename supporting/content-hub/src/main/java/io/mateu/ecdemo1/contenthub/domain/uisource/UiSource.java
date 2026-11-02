package io.mateu.ecdemo1.contenthub.domain.uisource;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * An external UI the intranet may show — the registry's entry (configuration, {@code content:admin}).
 * Editors never type where a UI lives: they choose a registered source and, for it, a route or a few
 * attributes ({@link RemoteUi}); the menus do the same.
 *
 * <p>Where it lives is never an arbitrary host: a Mateu UI or a web component is a path the gateway
 * routes ({@code /_loyalty}, {@code /_inbox/push/push.js}), so the user's token travels as it does for the
 * shells and the remote enforces its own permissions; only an iframe may point at another origin, and only
 * an https one the deployment allow-lists ({@link #requireAllowedOrigin}).
 *
 * <p>Who may use it: {@code audience} (the same expressions as content, evaluated by the one visibility
 * function — a page or block that uses the source is projected with it ANDed in, {@link #reference}) and
 * {@code requiredScopes} (the reader must hold all of them, as the gateway's {@code X-User-Scopes} says).
 * An inactive source is used by nobody. Both only ever narrow: what uses a source keeps its own audience too.
 *
 * @param basePath MATEU_REMOTE: the gateway prefix of its {@code @UI} ({@code /_loyalty}); WEB_COMPONENT: the
 *                 script that defines the element, behind the gateway; IFRAME: an https URL
 * @param tagName  WEB_COMPONENT: the custom element's name ({@code ec-push-toggle}); otherwise null
 */
public record UiSource(String id, String name, UiSourceKind kind, String basePath, String tagName, Audience audience,
                       Set<String> requiredScopes, boolean active) {

    /** The named-audience id under which a source's audience is known to the visibility policy. */
    public static final String AUDIENCE_PREFIX = "ui-source:";

    /** Admits nobody: an inactive source's audience. */
    public static final Audience NOBODY = new Audience.AnyOf(List.of());

    public static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,62}");
    /** A service's prefix at the gateway: {@code /_users}, {@code /_loyalty}. */
    static final Pattern GATEWAY_PREFIX = Pattern.compile("/_[a-z0-9][a-z0-9-]*");
    /** A script under a gateway prefix: {@code /_inbox/push/push.js}. */
    static final Pattern GATEWAY_SCRIPT = Pattern.compile("/_[a-z0-9][a-z0-9-]*(/[A-Za-z0-9_~-][A-Za-z0-9._~-]*)+\\.m?js");
    /** A custom element's name: lower case, with a hyphen (HTML's "valid custom element name", the ASCII part). */
    static final Pattern TAG = Pattern.compile("[a-z][a-z0-9._]*-[a-z0-9._-]*");
    static final Pattern SCOPE = Pattern.compile("[A-Za-z0-9:._-]{1,100}");
    /** Names HTML reserves, and the intranet's own elements: a source cannot redefine them. */
    static final Set<String> RESERVED_TAGS = Set.of("annotation-xml", "color-profile", "font-face", "font-face-src",
            "font-face-uri", "font-face-format", "font-face-name", "missing-glyph", "ec-ui-source", "ec-visual-editor");

    public UiSource {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("El id de una UI externa va en minúsculas, cifras y guiones (p. ej. riu-class)");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("La UI externa necesita un nombre");
        }
        if (kind == null) {
            throw new IllegalArgumentException("Falta el tipo de la UI externa");
        }
        basePath = basePath == null ? null : basePath.trim();
        tagName = tagName == null || tagName.isBlank() ? null : tagName.trim();
        switch (kind) {
            case MATEU_REMOTE -> {
                if (basePath == null || !GATEWAY_PREFIX.matcher(basePath).matches()) {
                    throw new IllegalArgumentException("Una UI de Mateu se registra por su prefijo en el gateway, "
                            + "como /_loyalty o /_users (no una URL ni otra ruta)");
                }
                if (tagName != null) {
                    throw new IllegalArgumentException("Solo un web component lleva etiqueta");
                }
            }
            case WEB_COMPONENT -> {
                if (basePath == null || !GATEWAY_SCRIPT.matcher(basePath).matches() || basePath.contains("/../")
                        || basePath.contains("/./")) {
                    throw new IllegalArgumentException("Un web component se registra por la ruta de su script en el "
                            + "gateway, como /_inbox/push/push.js");
                }
                if (tagName == null || !TAG.matcher(tagName).matches() || RESERVED_TAGS.contains(tagName)) {
                    throw new IllegalArgumentException("La etiqueta de un web component va en minúsculas y con un "
                            + "guion, como ec-push-toggle" + (tagName != null && RESERVED_TAGS.contains(tagName)
                            ? " («" + tagName + "» está reservada)" : ""));
                }
            }
            case IFRAME -> {
                https(basePath);
                if (tagName != null) {
                    throw new IllegalArgumentException("Solo un web component lleva etiqueta");
                }
            }
        }
        audience = audience == null ? Audience.EVERYONE : audience;
        var scopes = new TreeSet<String>();
        for (var s : requiredScopes == null ? Set.<String>of() : requiredScopes) {
            if (s == null || s.isBlank()) {
                continue;
            }
            if (!SCOPE.matcher(s.trim()).matches()) {
                throw new IllegalArgumentException("Permiso no válido: «" + s + "»");
            }
            scopes.add(s.trim());
        }
        requiredScopes = Set.copyOf(scopes);
    }

    /** The audience reference a page or block that uses source {@code sourceId} is projected with. */
    public static Audience reference(String sourceId) {
        return new Audience.Named(AUDIENCE_PREFIX + sourceId);
    }

    /** Whether a named-audience id is a source's ({@link #reference}). */
    public static boolean isReference(String namedAudienceId) {
        return namedAudienceId != null && namedAudienceId.startsWith(AUDIENCE_PREFIX);
    }

    public static String sourceIdOf(String namedAudienceId) {
        return namedAudienceId.substring(AUDIENCE_PREFIX.length());
    }

    /** What the policy resolves {@link #reference} to: its audience, or nobody while inactive. */
    public Audience visibilityAudience() {
        return active ? audience : NOBODY;
    }

    /** Whether a reader with these scopes may use it: active, and holding every required scope. */
    public boolean usableWith(Set<String> scopes) {
        return active && (requiredScopes.isEmpty() || scopes != null && scopes.containsAll(requiredScopes));
    }

    /** An iframe's origin ({@code https://host[:port]}); null for the other kinds. */
    public String origin() {
        if (kind != UiSourceKind.IFRAME) {
            return null;
        }
        var u = URI.create(basePath);
        return "https://" + u.getHost().toLowerCase(Locale.ROOT) + (u.getPort() == -1 ? "" : ":" + u.getPort());
    }

    /**
     * Checks an iframe source's origin against the deployment's allow-list ({@code https://host[:port]}
     * each). The other kinds live behind the gateway and need no list.
     */
    public void requireAllowedOrigin(Set<String> allowedOrigins) {
        if (kind == UiSourceKind.IFRAME && (allowedOrigins == null || !allowedOrigins.contains(origin()))) {
            throw new IllegalArgumentException("El origen " + origin() + " no está en la lista de orígenes permitidos "
                    + "para iframes (content-hub.ui-sources.iframe-origins)");
        }
    }

    /**
     * Whether a use fits this source: a Mateu UI is chosen by route only; a web component by attributes
     * (and an optional route, passed as its {@code route} attribute); an iframe by a route under its URL.
     */
    public void check(RemoteUi use) {
        if (use == null || !id.equals(use.sourceId())) {
            throw new IllegalArgumentException("Ese uso no es de la UI externa " + id);
        }
        if (kind == UiSourceKind.MATEU_REMOTE && !use.attributes().isEmpty()) {
            throw new IllegalArgumentException("Una UI de Mateu se elige por su ruta: no lleva atributos");
        }
    }

    /** IFRAME: the URL a use opens — the source's URL with the use's route under it and its attributes as query. */
    public String frameUrl(RemoteUi use) {
        var base = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
        var route = use.route() == null ? "" : use.route();
        var url = route.isEmpty() ? basePath : base + route;
        var query = use.query();
        return query.isEmpty() ? url : url + (url.contains("?") ? "&" : "?") + query;
    }

    static void https(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Un iframe necesita su URL https");
        }
        URI u;
        try {
            u = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("URL no válida: " + url);
        }
        if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null || u.getRawUserInfo() != null
                || u.getRawFragment() != null || url.chars().anyMatch(c -> c <= ' ' || c == '"' || c == '<' || c == '>')) {
            throw new IllegalArgumentException("Un iframe solo admite una URL https completa, sin usuario ni fragmento");
        }
    }
}
