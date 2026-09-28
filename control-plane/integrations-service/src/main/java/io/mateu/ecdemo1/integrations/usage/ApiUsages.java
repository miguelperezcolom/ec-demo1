package io.mateu.ecdemo1.integrations.usage;

import io.mateu.ecdemo1.integration.model.usage.ApiCalls;
import io.mateu.ecdemo1.integration.model.usage.ApiUsage;
import io.mateu.ecdemo1.integrations.config.TolerantReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The external APIs' usage as the consoles' header shows it: what each service that calls one counted,
 * added up — Opera may be called from more than one — and kept a few seconds, so a room full of open
 * consoles asks the services once, not once each. None of it costs Salesforce or Opera a call: the
 * services answer with what they already know.
 *
 * <p>A service that does not answer is left out; if none does, there is no usage to show, and the
 * header says so ("—") instead of a number that is not true.
 */
@Component
@Slf4j
public class ApiUsages {

    public static final String SALESFORCE = "salesforce";
    public static final String OPERA = "opera";

    record Kept(Optional<ApiUsage> usage, Instant at) {
    }

    final UsageProperties properties;
    final Clock clock;
    final RestClient rest;
    final Map<String, Kept> kept = new ConcurrentHashMap<>();

    public ApiUsages(UsageProperties properties, TolerantReader reader, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(properties.timeout());
        this.rest = RestClient.builder()
                .requestFactory(factory)
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.addFirst(new MappingJackson2HttpMessageConverter(reader.mapper()));
                })
                .build();
    }

    public Optional<ApiUsage> salesforce() {
        return usage(SALESFORCE, properties.salesforce());
    }

    public Optional<ApiUsage> opera() {
        return usage(OPERA, properties.opera());
    }

    Optional<ApiUsage> usage(String api, List<String> services) {
        var now = clock.instant();
        var last = kept.get(api);
        if (last != null && last.at().plus(properties.cache()).isAfter(now)) {
            return last.usage();
        }
        var answers = new ArrayList<ApiUsage>();
        for (var url : services) {
            try {
                answers.add(Objects.requireNonNull(fetch(url, api)));
            } catch (RuntimeException e) {
                log.debug("{} did not say its {} usage: {}", url, api, e.getMessage());
            }
        }
        var usage = answers.isEmpty() ? Optional.<ApiUsage>empty() : Optional.of(sum(api, answers));
        kept.put(api, new Kept(usage, now));
        return usage;
    }

    ApiUsage fetch(String baseUrl, String api) {
        return rest.get().uri(baseUrl + "/usage/{api}", api).retrieve().body(ApiUsage.class);
    }

    /**
     * Several services' counts as one: the calls added up, and the API's own word — the org total, the
     * rate limit — as the one that heard it last said it.
     */
    static ApiUsage sum(String api, List<ApiUsage> usages) {
        if (usages.size() == 1) {
            return usages.getFirst();
        }
        var latest = usages.stream().filter(u -> u.seenAt() != null).max(Comparator.comparing(ApiUsage::seenAt));
        var ours = usages.stream().mapToLong(ApiUsage::ours24h).sum();
        var byPurpose = new HashMap<String, Long>();
        var byEndpoint = new HashMap<String, Long>();
        usages.forEach(u -> {
            u.byPurpose().forEach((k, v) -> byPurpose.merge(k, v, Long::sum));
            u.byEndpoint().forEach((k, v) -> byEndpoint.merge(k, v, Long::sum));
        });
        var used = latest.map(ApiUsage::orgUsed).orElse(null);
        var pausedUntil = usages.stream().map(ApiUsage::pausedUntil).filter(Objects::nonNull).max(Comparator.naturalOrder());
        return new ApiUsage(api, String.join("+", usages.stream().map(ApiUsage::service).toList()), used,
                latest.map(ApiUsage::orgMax).orElse(null), latest.map(ApiUsage::seenAt).orElse(null), ours,
                ApiCalls.sortedByCount(byPurpose), ApiCalls.sortedByCount(byEndpoint),
                used == null ? null : Math.max(0, used - ours),
                usages.stream().mapToLong(ApiUsage::limited24h).sum(), usages.stream().mapToLong(ApiUsage::errors24h).sum(),
                usages.stream().anyMatch(ApiUsage::paused), pausedUntil.orElse(null),
                latest.map(ApiUsage::rateLimit).orElse(Map.of()), latest.map(ApiUsage::rateLimitRemaining).orElse(null),
                usages.stream().mapToLong(ApiUsage::ours1h).sum(), usages.stream().mapToLong(ApiUsage::oursPrevious1h).sum(),
                latest.map(ApiUsage::orgUsed1hAgo).orElse(null));
    }

    /** 14908 → "14.9k", 950 → "950": what fits in a header. */
    public static String compact(long n) {
        if (n < 1000) {
            return Long.toString(n);
        }
        if (n < 100_000) {
            var k = Math.floor(n / 100.0) / 10;
            return (k == Math.rint(k) ? Long.toString((long) k) : Double.toString(k)) + "k";
        }
        return Math.round(n / 1000.0) + "k";
    }
}
