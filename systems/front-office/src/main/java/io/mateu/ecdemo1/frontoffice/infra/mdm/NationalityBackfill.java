package io.mateu.ecdemo1.frontoffice.infra.mdm;

import io.mateu.ecdemo1.frontoffice.domain.guest.CustomerNationalities;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The nationality of the guests and companions the {@code customers} topic said nothing about since
 * the front office started keeping it — those it already had: asked of the MDM once each
 * ({@code GET /customers/{id}}), a few at a time. One the MDM does not know is kept as unknown and not
 * asked again; the MDM not answering leaves it for the next round.
 */
@Component
public class NationalityBackfill {

  static final Logger log = LoggerFactory.getLogger(NationalityBackfill.class);

  final CustomerNationalities nationalities;
  final RestClient mdm;

  public NationalityBackfill(CustomerNationalities nationalities, @Value("${frontoffice.mdm-url:}") String mdmUrl) {
    this.nationalities = nationalities;
    this.mdm = client(mdmUrl);
  }

  static RestClient client(String url) {
    if (url == null || url.isBlank()) {
      return null;
    }
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(2));
    factory.setReadTimeout(Duration.ofSeconds(3));
    return RestClient.builder().baseUrl(url).requestFactory(factory).build();
  }

  @Scheduled(initialDelayString = "${frontoffice.nationality-backfill-delay:PT30S}",
      fixedDelayString = "${frontoffice.nationality-backfill-every:PT5M}")
  public void backfill() {
    if (mdm == null) {
      return;
    }
    var asked = 0;
    for (var customerId : nationalities.unknown(50)) {
      try {
        var customer = mdm.get().uri("/customers/{id}", customerId).retrieve().body(java.util.Map.class);
        var nationality = customer == null || customer.get("nationality") == null ? null
            : String.valueOf(customer.get("nationality"));
        nationalities.put(customerId, nationality, "MDM");
        asked++;
      } catch (org.springframework.web.client.HttpStatusCodeException e) {
        // The MDM answered and has no customer by that code (a guest the demo seeded, a walk-in):
        // unknown for good, not asked again.
        nationalities.put(customerId, null, "MDM");
      } catch (RuntimeException e) {
        log.debug("The MDM did not answer for {}'s nationality: {}", customerId, e.getMessage());
        return;
      }
    }
    if (asked > 0) {
      log.info("Nationality of {} customer(s) asked of the MDM", asked);
    }
  }
}
