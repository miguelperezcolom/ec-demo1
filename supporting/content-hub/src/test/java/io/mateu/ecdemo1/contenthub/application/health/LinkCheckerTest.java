package io.mateu.ecdemo1.contenthub.application.health;

import io.mateu.ecdemo1.contenthub.application.out.HttpProbe;
import io.mateu.ecdemo1.contenthub.domain.health.LinkStatus;
import io.mateu.ecdemo1.contenthub.domain.health.LinkStatus.Outcome;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The link checker against a stub HTTP client: no request ever leaves the test. */
class LinkCheckerTest {

    /** Answers by URL; resolves names by a table (unknown name: no such host). */
    static final class StubProbe implements HttpProbe {
        final Map<String, Response> head = new HashMap<>();
        final Map<String, Response> get = new HashMap<>();
        final Set<String> timeouts = new java.util.HashSet<>();
        final Map<String, String> addresses = new HashMap<>();
        final List<String> requests = new ArrayList<>();
        final List<String> agents = new ArrayList<>();

        @Override
        public Response request(URI uri, String method, Duration timeout, String userAgent) throws IOException {
            requests.add(method + " " + uri);
            agents.add(userAgent);
            if (timeouts.contains(uri.toString())) {
                throw new HttpTimeoutException("timed out");
            }
            var r = (method.equals("HEAD") ? head : get).get(uri.toString());
            return r == null ? new Response(404, null) : r;
        }

        @Override
        public List<InetAddress> resolve(String host) throws UnknownHostException {
            var ip = addresses.get(host);
            if (ip == null) {
                throw new UnknownHostException(host);
            }
            return List.of(InetAddress.getByName(ip)); // a literal: no lookup
        }
    }

    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-02T03:15:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    final StubProbe probe = new StubProbe();
    final HealthFixtures.InMemoryHealth cache = new HealthFixtures.InMemoryHealth();
    final TestClock clock = new TestClock();
    final List<Duration> slept = new ArrayList<>();
    final LinkChecker.Sleeper sleeper = d -> {
        slept.add(d);
        clock.now = clock.now.plus(d);
    };

    LinkChecker checker(HealthSettings settings) {
        return new LinkChecker(probe, cache, settings, clock, sleeper);
    }

    LinkChecker checker() {
        return checker(HealthSettings.defaults());
    }

    static HealthSettings withBudget(int max) {
        var d = HealthSettings.defaults();
        return new HealthSettings(d.expiryDays(), d.staleMonths(), d.staleSections(), d.unviewedDays(), true,
                d.linkTimeout(), d.perHostDelay(), max, d.okFor(), d.brokenFor(), d.userAgent(), d.orphanGrace());
    }

    @Test
    void head_first_get_when_head_is_not_understood_and_the_answer_classified() {
        probe.addresses.put("ok.example", "93.184.216.34");
        probe.addresses.put("nohead.example", "93.184.216.35");
        probe.addresses.put("login.example", "93.184.216.36");
        probe.head.put("https://ok.example/", new HttpProbe.Response(200, null));
        probe.head.put("https://nohead.example/doc", new HttpProbe.Response(405, null));
        probe.get.put("https://nohead.example/doc", new HttpProbe.Response(200, null));
        probe.head.put("https://login.example/", new HttpProbe.Response(403, null));

        var result = checker().check(List.of("https://ok.example/", "https://nohead.example/doc",
                "https://login.example/", "https://ok.example/borrado"));

        assertThat(result.get("https://ok.example/").outcome()).isEqualTo(Outcome.OK);
        assertThat(result.get("https://nohead.example/doc").outcome()).isEqualTo(Outcome.OK);
        assertThat(result.get("https://login.example/").detail()).contains("acceso restringido");
        assertThat(result.get("https://ok.example/borrado")).satisfies(s -> {
            assertThat(s.outcome()).isEqualTo(Outcome.BROKEN);
            assertThat(s.detail()).isEqualTo("no existe (HTTP 404)");
        });
        assertThat(probe.requests).contains("HEAD https://nohead.example/doc", "GET https://nohead.example/doc");
        assertThat(probe.agents).allMatch(a -> a.startsWith("ec-demo1-content-health/"));
    }

    @Test
    void private_addresses_are_never_asked_not_even_through_a_redirect() {
        probe.addresses.put("intranet.local", "10.0.0.5");
        probe.addresses.put("loop.example", "127.0.0.1");
        probe.addresses.put("cgnat.example", "100.64.1.1");
        probe.addresses.put("v6.example", "fd00::1");
        probe.addresses.put("redir.example", "93.184.216.34");
        probe.head.put("https://redir.example/", new HttpProbe.Response(302, "http://intranet.local/admin"));

        var result = checker().check(List.of("http://intranet.local/", "http://loop.example/", "http://cgnat.example/",
                "http://v6.example/", "https://redir.example/"));

        assertThat(result.values()).allMatch(s -> s.outcome() == Outcome.SKIPPED);
        assertThat(result.get("https://redir.example/").detail()).contains("redirige a una dirección interna");
        assertThat(probe.requests).containsExactly("HEAD https://redir.example/");
    }

    @Test
    void redirects_are_followed_and_failures_named() {
        probe.addresses.put("a.example", "93.184.216.34");
        probe.addresses.put("b.example", "93.184.216.35");
        probe.addresses.put("slow.example", "93.184.216.36");
        probe.addresses.put("loop.example", "93.184.216.37");
        probe.head.put("https://a.example/old", new HttpProbe.Response(301, "https://b.example/new"));
        probe.head.put("https://b.example/new", new HttpProbe.Response(200, null));
        probe.timeouts.add("https://slow.example/");
        probe.head.put("https://loop.example/", new HttpProbe.Response(302, "/"));

        var result = checker().check(List.of("https://a.example/old", "https://nadie.invalid/", "https://slow.example/",
                "https://loop.example/", "ftp://files.example/x"));

        assertThat(result.get("https://a.example/old").outcome()).isEqualTo(Outcome.OK);
        assertThat(result.get("https://nadie.invalid/").detail()).isEqualTo("el dominio nadie.invalid no existe");
        assertThat(result.get("https://slow.example/").detail()).isEqualTo("sin respuesta en 5 s");
        assertThat(result.get("https://loop.example/").detail()).isEqualTo("demasiadas redirecciones");
        assertThat(result.get("ftp://files.example/x").outcome()).isEqualTo(Outcome.SKIPPED);
    }

    @Test
    void answers_are_cached_good_ones_for_a_week_bad_ones_for_a_day_and_429_not_at_all() {
        probe.addresses.put("ok.example", "93.184.216.34");
        probe.addresses.put("busy.example", "93.184.216.35");
        probe.head.put("https://ok.example/", new HttpProbe.Response(200, null));
        probe.head.put("https://busy.example/", new HttpProbe.Response(429, null));
        var urls = List.of("https://ok.example/", "https://ok.example/404", "https://busy.example/");

        checker().check(urls);
        assertThat(probe.requests).hasSize(3);
        assertThat(cache.links).containsOnlyKeys("https://ok.example/", "https://ok.example/404");
        assertThat(checker().check(urls).get("https://busy.example/").outcome()).isEqualTo(Outcome.UNKNOWN);
        assertThat(probe.requests).as("only the 429 is asked again").hasSize(4);

        clock.now = clock.now.plus(Duration.ofDays(2));
        checker().check(urls);
        assertThat(probe.requests).as("the broken one is asked again after a day, the good one is not")
                .filteredOn(r -> r.contains("ok.example")).containsExactly("HEAD https://ok.example/",
                        "HEAD https://ok.example/404", "HEAD https://ok.example/404");

        clock.now = clock.now.plus(Duration.ofDays(6));
        checker().check(urls);
        assertThat(probe.requests).filteredOn(r -> r.equals("HEAD https://ok.example/")).hasSize(2);
    }

    @Test
    void one_request_per_host_at_a_time_and_a_budget_per_run() {
        probe.addresses.put("one.example", "93.184.216.34");
        probe.addresses.put("two.example", "93.184.216.35");
        var urls = List.of("https://one.example/1", "https://one.example/2", "https://two.example/1",
                "https://one.example/3", "https://one.example/4");

        var result = checker(withBudget(4)).check(urls);

        assertThat(probe.requests).hasSize(4);
        assertThat(slept).as("a pause before each repeated request to one.example").hasSize(2)
                .allMatch(d -> !d.isNegative() && d.compareTo(Duration.ofSeconds(1)) <= 0);
        assertThat(result.get("https://one.example/4")).isEqualTo(LinkStatus.unknown("https://one.example/4"));
    }

    @Test
    void private_ranges_are_recognised() throws Exception {
        for (var ip : List.of("10.1.2.3", "172.16.0.1", "192.168.1.1", "127.0.0.1", "169.254.1.1", "0.0.0.0",
                "100.100.0.1", "::1", "fe80::1", "fc00::1", "::ffff:10.0.0.1")) {
            assertThat(LinkChecker.isPrivate(InetAddress.getByName(ip))).as(ip).isTrue();
        }
        for (var ip : List.of("8.8.8.8", "93.184.216.34", "100.128.0.1", "2001:4860:4860::8888")) {
            assertThat(LinkChecker.isPrivate(InetAddress.getByName(ip))).as(ip).isFalse();
        }
    }
}
