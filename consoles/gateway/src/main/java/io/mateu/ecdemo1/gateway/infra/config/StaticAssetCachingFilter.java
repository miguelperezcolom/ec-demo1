package io.mateu.ecdemo1.gateway.infra.config;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Lets the browser cache the consoles' static assets.
 *
 * <p>Every backend behind this gateway is a Spring Boot app with Spring Security, and Spring
 * Security's default headers put {@code Cache-Control: no-cache, no-store, max-age=0,
 * must-revalidate} on every response that does not already carry one — static files included,
 * because nothing upstream (Spring's resource handler, Mateu) sets one. The result was a console
 * that re-downloaded all of its JavaScript on every load: 5.4 MB for the front office, the 777 KB
 * Redwood app bundle and the 885 KB Vaadin one among it.
 *
 * <p>Two rules, applied to successful GETs only, so an error page or a 404 is never cached:
 * <ul>
 *   <li>Redwood's {@code /version_<n>/…}: the build stamps a new number into the path whenever the
 *       bundle changes, so whatever is behind a given path never changes. {@code immutable} for a
 *       year — a second load does not even ask.
 *   <li>Any other static file (a path ending in a known asset extension — Vaadin's
 *       {@code /assets/mateu-vaadin.js}, keycloak.min.js, the push scripts): its name is fixed
 *       across releases, so it cannot be cached blindly. {@code no-cache} instead of
 *       {@code no-store}: the browser keeps it and revalidates, and the backends already send
 *       Last-Modified, so an unchanged file is a 304 with no body.
 * </ul>
 *
 * <p>Only a response that came back with {@code no-store} is touched: a backend that chose a
 * caching policy of its own (Mateu once it ships one) keeps it. The bootstrap page and every
 * API response are left exactly as they were.
 *
 * <p>Interim: Mateu's fix (fix/static-asset-caching) sets these headers in the shells themselves.
 * Once every image runs a Mateu with it, this filter finds no {@code no-store} on those paths and
 * does nothing.
 */
@Component
public class StaticAssetCachingFilter implements GlobalFilter, Ordered {

    static final String IMMUTABLE = "public, max-age=31536000, immutable";
    static final String REVALIDATE = "no-cache";

    private static final Pattern VERSIONED = Pattern.compile("^/version_\\d+/.+");

    private static final Set<String> STATIC_EXTENSIONS = Set.of(
            "js", "mjs", "css", "map", "json", "svg", "png", "jpg", "jpeg", "gif", "webp", "ico",
            "woff", "woff2", "ttf", "otf", "eot", "txt", "html");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        if (request.getMethod() != HttpMethod.GET && request.getMethod() != HttpMethod.HEAD) {
            return chain.filter(exchange);
        }
        String policy = policyFor(request.getPath().value());
        if (policy == null) {
            return chain.filter(exchange);
        }
        var response = exchange.getResponse();
        response.beforeCommit(() -> {
            apply(policy, response.getStatusCode() == null ? 0 : response.getStatusCode().value(),
                    response.getHeaders());
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    /** The policy for a path, or null when the path is not a static asset this filter handles. */
    static String policyFor(String path) {
        if (VERSIONED.matcher(path).matches()) {
            return IMMUTABLE;
        }
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        if (dot <= slash || dot == path.length() - 1) {
            return null;
        }
        String extension = path.substring(dot + 1).toLowerCase(Locale.ROOT);
        // _index.html is the template behind the bootstrap page, never fetched by the browser; an
        // .html path otherwise is a static page and revalidates like any other file.
        return STATIC_EXTENSIONS.contains(extension) ? REVALIDATE : null;
    }

    /** Rewrites the headers of a successful response that came back as no-store. */
    static void apply(String policy, int status, HttpHeaders headers) {
        boolean ok = status == HttpStatus.OK.value() || status == HttpStatus.NOT_MODIFIED.value()
                || status == HttpStatus.PARTIAL_CONTENT.value();
        if (!ok) {
            return;
        }
        String current = headers.getFirst(HttpHeaders.CACHE_CONTROL);
        if (current != null && !current.toLowerCase(Locale.ROOT).contains("no-store")) {
            return;
        }
        headers.set(HttpHeaders.CACHE_CONTROL, policy);
        headers.remove(HttpHeaders.PRAGMA);
        headers.remove(HttpHeaders.EXPIRES);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
