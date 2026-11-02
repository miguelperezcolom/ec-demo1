package io.mateu.ecdemo1.contenthub.domain.uisource;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A use of a registered UI source — what a remote page or a «Componente remoto» block shows: which source,
 * and in it a route (a Mateu UI's screen, a path under an iframe's URL) or a few attributes (a web
 * component's). Never a URL of its own: where the source lives is the registry's business.
 *
 * <p>A remote page stores it in its URL as {@code ui:<sourceId><route>[?name=value&…]} ({@link #uri},
 * {@link #parse}), as a document stores {@code media:<id>}.
 */
public record RemoteUi(String sourceId, String route, Map<String, String> attributes) {

    public static final String SCHEME = "ui:";

    /** A path of the remote, without query or fragment: {@code /loyalty/members}, {@code /}. No {@code //}, no dot segments. */
    static final Pattern ROUTE = Pattern.compile("(/[A-Za-z0-9_~:@!$'()*+,;=-][A-Za-z0-9._~:@!$'()*+,;=-]*)*/?");
    static final Pattern ATTRIBUTE = Pattern.compile("[a-z][a-z0-9-]{0,40}");
    /** Attributes a use cannot set: event handlers, and what would restyle or re-slot the element. */
    static final Set<String> RESERVED_ATTRIBUTES = Set.of("style", "is", "slot", "class", "id");
    static final int MAX_ATTRIBUTES = 20;
    static final int MAX_VALUE = 500;

    public RemoteUi {
        if (sourceId == null || !UiSource.ID.matcher(sourceId).matches()) {
            throw new IllegalArgumentException("Elige una de las UIs externas registradas");
        }
        route = route == null || route.isBlank() ? null : route.trim();
        if (route != null && (!ROUTE.matcher(route).matches() || route.contains("/./") || route.contains("/../")
                || route.endsWith("/.") || route.endsWith("/.."))) {
            throw new IllegalArgumentException("La ruta empieza por / y es solo un camino (sin ?, #, //, .. ni espacios): «"
                    + route + "»");
        }
        var attrs = new TreeMap<String, String>();
        for (var e : (attributes == null ? Map.<String, String>of() : attributes).entrySet()) {
            var name = e.getKey() == null ? "" : e.getKey().trim();
            if (name.isEmpty()) {
                continue;
            }
            if (!ATTRIBUTE.matcher(name).matches() || name.startsWith("on") || RESERVED_ATTRIBUTES.contains(name)) {
                throw new IllegalArgumentException("Atributo no admitido: «" + name + "» (minúsculas, cifras y guiones; "
                        + "ni on…, ni style, class, id, is o slot)");
            }
            var value = e.getValue() == null ? "" : e.getValue();
            if (value.length() > MAX_VALUE || value.chars().anyMatch(c -> c < ' ' && c != '\t')) {
                throw new IllegalArgumentException("El valor del atributo «" + name + "» es demasiado largo o lleva caracteres de control");
            }
            var lower = value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s", "");
            if (lower.startsWith("javascript:") || lower.startsWith("vbscript:") || lower.startsWith("data:")) {
                throw new IllegalArgumentException("El valor del atributo «" + name + "» no puede ser un javascript:, vbscript: o data:");
            }
            attrs.put(name, value);
        }
        if (attrs.size() > MAX_ATTRIBUTES) {
            throw new IllegalArgumentException("Demasiados atributos (máximo " + MAX_ATTRIBUTES + ")");
        }
        attributes = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(attrs));
    }

    public static RemoteUi of(String sourceId, String route) {
        return new RemoteUi(sourceId, route, Map.of());
    }

    /** The audience reference its source contributes to whatever uses it (see {@link UiSource#reference}). */
    public Audience audienceReference() {
        return UiSource.reference(sourceId);
    }

    /** {@code ui:<sourceId><route>[?a=b&…]}: how a remote page keeps it in its URL. */
    public String uri() {
        var q = query();
        return SCHEME + sourceId + (route == null ? "" : route) + (q.isEmpty() ? "" : "?" + q);
    }

    /** The attributes as a query string, URL-encoded, in name order. */
    public String query() {
        return attributes.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    public static boolean isRemote(String url) {
        return url != null && url.startsWith(SCHEME);
    }

    /**
     * A remote page's URL back into its use; empty when it is not one ({@link #isRemote}). A malformed one
     * is refused, as when it was saved.
     */
    public static Optional<RemoteUi> parse(String url) {
        if (!isRemote(url)) {
            return Optional.empty();
        }
        var rest = url.substring(SCHEME.length()).trim();
        String query = null;
        var q = rest.indexOf('?');
        if (q >= 0) {
            query = rest.substring(q + 1);
            rest = rest.substring(0, q);
        }
        var slash = rest.indexOf('/');
        var id = slash < 0 ? rest : rest.substring(0, slash);
        var route = slash < 0 ? null : rest.substring(slash);
        var attrs = new LinkedHashMap<String, String>();
        if (query != null && !query.isBlank()) {
            for (var pair : query.split("&")) {
                if (pair.isEmpty()) {
                    continue;
                }
                var eq = pair.indexOf('=');
                var name = eq < 0 ? pair : pair.substring(0, eq);
                var value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                attrs.put(name, value);
            }
        }
        return Optional.of(new RemoteUi(id, route, attrs));
    }

    /** Attributes written one per line as {@code name=value} (the forms' text) — blank lines skipped. */
    public static Map<String, String> attributesOf(String lines) {
        var out = new LinkedHashMap<String, String>();
        if (lines == null) {
            return out;
        }
        for (var line : lines.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            var eq = line.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("Cada atributo va en su línea como nombre=valor: «" + line.trim() + "»");
            }
            out.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        return out;
    }

    /** The inverse of {@link #attributesOf}. */
    public String attributeLines() {
        return attributes.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("\n"));
    }
}
