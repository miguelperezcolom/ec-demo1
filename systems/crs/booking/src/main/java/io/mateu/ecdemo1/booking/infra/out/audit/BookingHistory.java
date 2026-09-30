package io.mateu.ecdemo1.booking.infra.out.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A booking's history — who did what with it, here and at the hotel's front office, and when — as the
 * audit service keeps it ({@code GET /audit}, inside the cluster). An audit service that does not
 * answer leaves the history unknown, not the booking's page broken.
 */
@Slf4j
@Component
public class BookingHistory {

    /** One action: when, by whom, on which service, done or not, and what it answered. */
    public record Entry(Instant at, String service, String action, String by, boolean succeeded, String response) {
    }

    final RestClient audit;

    public BookingHistory(@Value("${audit.url:}") String url) {
        if (url == null || url.isBlank()) {
            this.audit = null;
        } else {
            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(2));
            factory.setReadTimeout(Duration.ofSeconds(3));
            this.audit = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        }
    }

    /** Newest first; empty when the audit service is not configured or does not answer. */
    @SuppressWarnings("unchecked")
    public Optional<List<Entry>> of(String locator) {
        if (audit == null || locator == null || locator.isBlank()) {
            return Optional.empty();
        }
        try {
            var answer = audit.get()
                    .uri(b -> b.path("/audit").queryParam("locator", locator).queryParam("stayId", locator)
                            .queryParam("limit", 50).build())
                    .retrieve().body(List.class);
            return Optional.of(answer == null ? List.of()
                    : ((List<Map<String, Object>>) answer).stream().map(BookingHistory::entry).toList());
        } catch (RuntimeException e) {
            log.debug("The history of {} could not be read: {}", locator, e.getMessage());
            return Optional.empty();
        }
    }

    static Entry entry(Map<String, Object> m) {
        Instant at = null;
        try {
            at = m.get("at") == null ? null : Instant.parse(String.valueOf(m.get("at")));
        } catch (RuntimeException e) {
            // shown without its time
        }
        return new Entry(at, text(m.get("service")), text(m.get("action")), text(m.get("by")),
                Boolean.TRUE.equals(m.get("succeeded")), text(m.get("response")));
    }

    static String text(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
