package io.mateu.ecdemo1.frontoffice.infra.mdm;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Where a stay's people and its reservation are in the chain's other systems — the customer in the
 * data plane's Clientes, its contact in Salesforce, its profile in Opera, the booking in the CRS —
 * as the MDM, which keeps the cross references, knows them. Read when the stay is shown; a MDM that
 * does not answer leaves only the link to the CRS booking, which needs nobody.
 */
@Slf4j
@Service
public class CrossLinks {

  /** Something to open elsewhere; no href when there is nowhere to go (Opera has no stable deep link). */
  public record Link(String what, String label, String href) {}

  private static CrossLinks instance;

  final RestClient mdm;
  final String hotel;
  final String console;

  public CrossLinks(@Value("${frontoffice.mdm-url:}") String mdmUrl, @Value("${frontoffice.hotel:MRU01}") String hotel,
      @Value("${frontoffice.console-url:https://ec1.mateu.io}") String console) {
    if (mdmUrl == null || mdmUrl.isBlank()) {
      this.mdm = null;
    } else {
      var factory = new SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(Duration.ofSeconds(2));
      factory.setReadTimeout(Duration.ofSeconds(3));
      this.mdm = RestClient.builder().baseUrl(mdmUrl).requestFactory(factory).build();
    }
    this.hotel = hotel;
    this.console = console == null ? "" : console.trim().replaceAll("/+$", "");
    instance = this;
  }

  /** The links of a stay: its id is the CRS locator. */
  public static List<Link> of(String locator) {
    return instance == null ? List.of() : instance.linksOf(locator);
  }

  @SuppressWarnings("unchecked")
  List<Link> linksOf(String locator) {
    var links = new ArrayList<Link>();
    // The desk's demo stays («+ 10 reservas demo») never came from the CRS.
    if (locator == null || locator.isBlank() || locator.startsWith("demo-")) {
      return links;
    }
    links.add(new Link("Reserva en el CRS", shortId(locator), console + "/booking/bookings/" + encode(locator)));
    // Its journey across the chain — CRS, integración, motor, Opera, este front office — on the data
    // plane's console (journey-service).
    links.add(new Link("Recorrido de la reserva", "Ver recorrido", console + "/journey/bookings/" + encode(locator)));
    if (mdm == null) {
      return links;
    }
    try {
      var answer = mdm.get().uri("/reservations/{hotel}/{locator}/links", hotel, locator).retrieve().body(Map.class);
      if (answer == null) {
        return links;
      }
      for (var p : (List<Map<String, Object>>) answer.getOrDefault("passengers", List.of())) {
        var who = "HOLDER".equals(p.get("role")) ? "Titular" : "Huésped " + (((Number) p.get("passenger")).intValue() + 1);
        var name = String.valueOf(p.getOrDefault("name", ""));
        links.add(new Link(who + " · cliente", name + " · " + p.get("customerId"),
            p.get("customerRoute") == null ? null : console + p.get("customerRoute")));
        if (p.get("salesforceContactId") != null) {
          links.add(new Link(who + " · Salesforce", "Contacto " + p.get("salesforceContactId"),
              (String) p.get("salesforceContactUrl")));
        }
      }
      for (var o : (List<Map<String, Object>>) answer.getOrDefault("operaProfiles", List.of())) {
        links.add(new Link("Opera · perfil", String.valueOf(o.get("profileId")), null));
      }
    } catch (RuntimeException e) {
      log.debug("The MDM did not answer for {}: {}", locator, e.getMessage());
    }
    return links;
  }

  static String shortId(String id) {
    var dash = id.lastIndexOf('-');
    return id.length() > 20 && dash > 0 ? "…" + id.substring(dash) : id;
  }

  static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
