package io.mateu.ecdemo1.frontoffice.infra.customer;

import io.mateu.ecdemo1.frontoffice.domain.customer.LoyaltyStatus;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Riu Class, the loyalty service ({@code frontoffice.loyalty-url}): {@code GET /members/{number}} when
 * the desk has the member number, else {@code GET /members?customerCode=}; 404 is not a member. Short
 * timeouts, answers kept a little while; not configured or not answering, no loyalty is shown — the
 * headers keep the guest's own figures.
 */
@Slf4j
@Component
public class HttpLoyaltyStatus implements LoyaltyStatus {

  public static final String SOURCE = "Riu Class";

  final RestClient quick;
  final RestClient writes;
  final Answers<Loyalty> answers = new Answers<>(Duration.ofSeconds(30), Duration.ofSeconds(10));

  public HttpLoyaltyStatus(@Value("${frontoffice.loyalty-url:}") String loyaltyUrl) {
    this.quick = Http.quick(loyaltyUrl);
    this.writes = Http.writes(loyaltyUrl);
  }

  @Override
  public Optional<Loyalty> of(String customerCode, String riuClassNumber) {
    if (quick == null || (blank(customerCode) && blank(riuClassNumber))) {
      return Optional.empty();
    }
    return answers.get(customerCode + "|" + riuClassNumber, () -> ask(customerCode, riuClassNumber));
  }

  Optional<Loyalty> ask(String customerCode, String riuClassNumber) {
    try {
      RestClient.RequestHeadersSpec<?> spec = blank(riuClassNumber)
          ? quick.get().uri(b -> b.path("/members").queryParam("customerCode", customerCode).build())
          : quick.get().uri("/members/{number}", riuClassNumber);
      return spec.exchange((request, response) -> {
        var status = response.getStatusCode().value();
        if (status == 404) {
          return Optional.<Loyalty>empty();
        }
        if (status / 100 != 2) {
          log.info("The loyalty service answered {} for {}: no loyalty shown", status, customerCode);
          return null;
        }
        Map<?, ?> m = response.bodyTo(Map.class);
        if (m == null || Http.text(m, "tier") == null) {
          return Optional.<Loyalty>empty();
        }
        return Optional.of(new Loyalty(Http.text(m, "tier"), Http.integer(m, "points"), Http.text(m, "memberNumber"),
            Http.date(m, "memberSince"), Http.date(m, "asOf"), SOURCE));
      });
    } catch (RuntimeException e) {
      log.info("The loyalty service could not be asked for {} ({}): no loyalty shown", customerCode, e.getMessage());
      return null;
    }
  }

  @Override
  public boolean enroll(String memberNumber, String customerCode, String tier, int points, LocalDate memberSince) {
    if (writes == null) {
      return false;
    }
    var body = new LinkedHashMap<String, Object>();
    body.put("customerCode", customerCode);
    body.put("tier", tier);
    body.put("points", points);
    body.put("memberSince", memberSince == null ? null : memberSince.toString());
    try {
      writes.put().uri("/members/{number}", memberNumber).contentType(MediaType.APPLICATION_JSON).body(body)
          .retrieve().toBodilessEntity();
      answers.kept.clear();
      return true;
    } catch (RuntimeException e) {
      log.warn("The loyalty service did not take member {} ({})", memberNumber, e.getMessage());
      return false;
    }
  }

  static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}
