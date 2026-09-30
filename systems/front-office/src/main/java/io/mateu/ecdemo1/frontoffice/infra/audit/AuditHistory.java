package io.mateu.ecdemo1.frontoffice.infra.audit;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * A reservation's history — who did what on the stay, or on the CRS's booking of its locator, and
 * when — as the audit service keeps it, asked for when the reservation is shown. Inside the cluster
 * ({@code GET /audit}); an audit service that does not answer leaves the history unknown, not the page
 * broken. Read into maps: the front office runs on Jackson 3, and nothing here is kept.
 */
@Service
public class AuditHistory {

  static final Logger log = LoggerFactory.getLogger(AuditHistory.class);

  /** One action on the reservation: when, by whom, on which service, done or not, and what it answered. */
  public record Entry(Instant at, String service, String action, String by, boolean succeeded, String response) {}

  private static AuditHistory instance;

  final RestClient audit;

  public AuditHistory(@Value("${frontoffice.audit-url:}") String auditUrl) {
    if (auditUrl == null || auditUrl.isBlank()) {
      this.audit = null;
    } else {
      var factory = new SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(Duration.ofSeconds(2));
      factory.setReadTimeout(Duration.ofSeconds(3));
      this.audit = RestClient.builder().baseUrl(auditUrl).requestFactory(factory).build();
    }
    instance = this;
  }

  /** The history of a stay and of its CRS locator, newest first; empty when the audit service is not there. */
  public static Optional<List<Entry>> of(String stayId, String locator) {
    return instance == null ? Optional.empty() : instance.historyOf(stayId, locator);
  }

  @SuppressWarnings("unchecked")
  Optional<List<Entry>> historyOf(String stayId, String locator) {
    if (audit == null) {
      return Optional.empty();
    }
    try {
      var answer = audit.get()
          .uri(b -> b.path("/audit").queryParam("stayId", stayId).queryParam("locator", locator)
              .queryParam("limit", 50).build())
          .retrieve().body(List.class);
      if (answer == null) {
        return Optional.of(List.of());
      }
      return Optional.of(((List<Map<String, Object>>) answer).stream().map(AuditHistory::entry).toList());
    } catch (RuntimeException e) {
      log.debug("The history of {} could not be read: {}", stayId, e.getMessage());
      return Optional.empty();
    }
  }

  static Entry entry(Map<String, Object> m) {
    Instant at = null;
    try {
      at = m.get("at") == null ? null : Instant.parse(String.valueOf(m.get("at")));
    } catch (RuntimeException e) {
      // unreadable: shown without its time
    }
    return new Entry(at, text(m.get("service")), text(m.get("action")), text(m.get("by")),
        Boolean.TRUE.equals(m.get("succeeded")), text(m.get("response")));
  }

  static String text(Object o) {
    return o == null ? null : String.valueOf(o);
  }
}
