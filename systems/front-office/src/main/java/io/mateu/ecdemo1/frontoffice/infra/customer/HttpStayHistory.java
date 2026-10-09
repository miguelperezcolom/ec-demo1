package io.mateu.ecdemo1.frontoffice.infra.customer;

import io.mateu.ecdemo1.frontoffice.domain.customer.StayHistory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The customer history ({@code frontoffice.history-url}): {@code GET /customers/{code}/summary}, the
 * summary the desk shows of a customer it knows. Short timeouts, answers kept a little while
 * ({@link Answers}); not configured or not answering, there is no summary and the screens show what
 * they showed before.
 */
@Slf4j
@Component
public class HttpStayHistory implements StayHistory {

  final RestClient quick;
  final RestClient writes;
  final Answers<HistorySummary> answers = new Answers<>(Duration.ofSeconds(30), Duration.ofSeconds(10));

  public HttpStayHistory(@Value("${frontoffice.history-url:}") String historyUrl) {
    this.quick = Http.quick(historyUrl);
    this.writes = Http.writes(historyUrl);
  }

  @Override
  public Optional<HistorySummary> summary(String customerCode) {
    if (quick == null || customerCode == null || customerCode.isBlank()) {
      return Optional.empty();
    }
    return answers.get(customerCode, () -> ask(customerCode));
  }

  Optional<HistorySummary> ask(String customerCode) {
    try {
      Map<?, ?> s = quick.get().uri("/customers/{code}/summary", customerCode).retrieve().body(Map.class);
      if (s == null) {
        return Optional.empty();
      }
      var last = new ArrayList<LastStay>();
      for (var item : Http.list(s, "lastStays")) {
        if (item instanceof Map<?, ?> l) {
          last.add(new LastStay(Http.text(l, "hotelCode"), Http.date(l, "arrival"), Http.date(l, "departure"),
              Http.text(l, "roomNumber"), Http.text(l, "roomType")));
        }
      }
      var spend = Http.map(s, "spend");
      return Optional.of(new HistorySummary(Http.text(s, "customerId") == null ? customerCode : Http.text(s, "customerId"),
          Http.integer(s, "stays"), Http.integer(s, "nights"), Http.date(s, "firstStay"), Http.date(s, "lastStay"), last,
          Http.integer(s, "hotels"), Http.text(s, "topHotel"), Http.decimal(spend, "amount"), Http.text(spend, "currency")));
    } catch (RuntimeException e) {
      log.info("The customer history could not be asked for {} ({}): no summary", customerCode, e.getMessage());
      return null;
    }
  }

  @Override
  public boolean seedDemo(String customerCode, int count) {
    if (writes == null) {
      return false;
    }
    try {
      writes.post().uri("/demo/stays").contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("customerId", customerCode, "count", count)).retrieve().toBodilessEntity();
      answers.forget(customerCode);
      return true;
    } catch (RuntimeException e) {
      log.warn("The customer history did not take the demo stays of {} ({})", customerCode, e.getMessage());
      return false;
    }
  }
}
