package io.mateu.ecdemo1.contenthub.application.health;

import io.mateu.ecdemo1.contenthub.application.out.HealthRepository;
import io.mateu.ecdemo1.contenthub.application.out.HttpProbe;
import io.mateu.ecdemo1.contenthub.domain.health.LinkStatus;
import io.mateu.ecdemo1.contenthub.domain.health.LinkStatus.Outcome;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Asks external URLs whether they are there, politely: what was asked recently is not asked again (a good
 * answer is kept a week, a bad one a day), one request per host at a time with a pause between them, HEAD
 * first and GET only when HEAD is not understood, a User-Agent that says who asks, a cap per run (the rest
 * wait for the next), and never an address inside the network — a link to one is skipped, not followed,
 * and so is a redirect to one. 401 and 403 count as "it exists": many links are behind a login.
 */
public class LinkChecker {

    /** Waits; a test gives one that only notes how long. */
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    static final int MAX_REDIRECTS = 5;

    final HttpProbe probe;
    final HealthRepository cache;
    final HealthSettings settings;
    final Clock clock;
    final Sleeper sleeper;
    final Map<String, Instant> lastRequestByHost = new HashMap<>();

    public LinkChecker(HttpProbe probe, HealthRepository cache, HealthSettings settings, Clock clock, Sleeper sleeper) {
        this.probe = probe;
        this.cache = cache;
        this.settings = settings;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /** What each URL answers: from the cache when fresh, asked otherwise while the run's budget lasts. */
    public Map<String, LinkStatus> check(Collection<String> urls) {
        var out = new LinkedHashMap<String, LinkStatus>();
        int asked = 0;
        for (var url : urls) {
            var cached = cache.cachedLink(url).orElse(null);
            if (cached != null && cached.freshAt(clock.instant(), settings.okFor(), settings.brokenFor())) {
                out.put(url, cached);
                continue;
            }
            if (!settings.externalLinks() || asked >= settings.maxExternalPerRun()) {
                out.put(url, cached != null ? cached : LinkStatus.unknown(url));
                continue;
            }
            asked++;
            var status = probe(url);
            if (status.outcome() != Outcome.UNKNOWN) {
                cache.cacheLink(status);
            }
            out.put(url, status);
        }
        return out;
    }

    /** One URL, following up to five redirects, each hop checked as the first. */
    public LinkStatus probe(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim().replace(" ", "%20"));
        } catch (IllegalArgumentException e) {
            return status(url, Outcome.BROKEN, 0, "URL mal formada");
        }
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return status(url, Outcome.SKIPPED, 0, "no es http(s)");
            }
            var host = uri.getHost();
            if (host == null || host.isBlank()) {
                return status(url, Outcome.BROKEN, 0, "URL sin dominio");
            }
            try {
                for (var address : probe.resolve(host)) {
                    if (isPrivate(address)) {
                        return status(url, Outcome.SKIPPED, 0, hop == 0 ? "dirección interna: no se comprueba"
                                : "redirige a una dirección interna: no se sigue");
                    }
                }
            } catch (UnknownHostException e) {
                return status(url, Outcome.BROKEN, 0, "el dominio " + host + " no existe");
            }
            HttpProbe.Response response;
            try {
                politely(host);
                response = probe.request(uri, "HEAD", settings.linkTimeout(), settings.userAgent());
                if (response.status() == 405 || response.status() == 501 || response.status() == 400) {
                    politely(host);
                    response = probe.request(uri, "GET", settings.linkTimeout(), settings.userAgent());
                }
            } catch (HttpTimeoutException e) {
                return status(url, Outcome.BROKEN, 0, "sin respuesta en " + settings.linkTimeout().toSeconds() + " s");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return LinkStatus.unknown(url);
            } catch (IOException e) {
                return status(url, Outcome.BROKEN, 0, "no se puede conectar");
            }
            var code = response.status();
            if (code >= 300 && code < 400 && response.location() != null && !response.location().isBlank()) {
                try {
                    uri = uri.resolve(response.location().trim().replace(" ", "%20"));
                } catch (IllegalArgumentException e) {
                    return status(url, Outcome.BROKEN, code, "redirige a una URL mal formada");
                }
                continue;
            }
            if (code == 429) {
                return new LinkStatus(url, Outcome.UNKNOWN, code, "pide esperar (429): se mirará en otra pasada", null);
            }
            if ((code >= 200 && code < 400) || code == 401 || code == 403) {
                return status(url, Outcome.OK, code, code == 401 || code == 403 ? "existe, con acceso restringido" : "HTTP " + code);
            }
            return status(url, Outcome.BROKEN, code, code == 404 || code == 410 ? "no existe (HTTP " + code + ")" : "HTTP " + code);
        }
        return status(url, Outcome.BROKEN, 0, "demasiadas redirecciones");
    }

    /** The pause since the last request to the same host. */
    void politely(String host) throws InterruptedException {
        var key = host.toLowerCase(Locale.ROOT);
        var last = lastRequestByHost.get(key);
        if (last != null) {
            var wait = Duration.between(clock.instant(), last.plus(settings.perHostDelay()));
            if (!wait.isNegative() && !wait.isZero()) {
                sleeper.sleep(wait);
            }
        }
        lastRequestByHost.put(key, clock.instant());
    }

    LinkStatus status(String url, Outcome outcome, int code, String detail) {
        return new LinkStatus(url, outcome, code, detail, clock.instant());
    }

    /** Loopback, link-local, private ranges (10/8, 172.16/12, 192.168/16, fc00::/7), CGNAT 100.64/10, 0/8, multicast. */
    public static boolean isPrivate(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) {
            return true;
        }
        var b = a.getAddress();
        if (a instanceof Inet6Address) {
            return (b[0] & 0xfe) == 0xfc;
        }
        return (b[0] & 0xff) == 0 || ((b[0] & 0xff) == 100 && (b[1] & 0xc0) == 64);
    }
}
