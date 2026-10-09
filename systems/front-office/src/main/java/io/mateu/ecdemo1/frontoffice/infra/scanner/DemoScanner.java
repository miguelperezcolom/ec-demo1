package io.mateu.ecdemo1.frontoffice.infra.scanner;

import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.infra.scanner.DemoDocuments.Scanned;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * The desk's <b>demo</b> scanner: there is no document reader at a demo's counter, so it makes up the
 * document the pax would hand over — believable, and always the same for the same person:
 *
 * <ol>
 *   <li>The document the stay already has for the pax, if it is a real one (not a made-up {@code MAN-…}).</li>
 *   <li>A customer of the chain's MDM with the pax's name who already has a document: <em>that</em>
 *       document — with its birth date and nationality. It is how the demo's returning customer, booked
 *       again under another email, hands over the document the chain already knows her by, and the MDM
 *       settles the duplicate at the desk.</li>
 *   <li>The one the booking carries, if it carries one.</li>
 *   <li>Otherwise {@link DemoDocuments}: a DNI for a Spaniard, a passport for anyone else — the
 *       nationality the booking gives —, a birth date that fits an adult or the child's age.</li>
 * </ol>
 *
 * Reading the booking (crs-integration) and the MDM are queries, for a screen that waits: over HTTP,
 * with short timeouts; one that does not answer is left out and the scanner still reads a document.
 */
@Slf4j
@Component
public class DemoScanner {

  /** Who is being scanned: the pax of a stay, as the desk has it. */
  public record Pax(String locator, int number, String name, String currentDocument, String customerId,
                    LocalDate arrival) {}

  /** A person of the booking, as the CRS has it. */
  record Booked(String firstName, String lastName, String nationality, Integer age, LocalDate birthDate,
                String documentType, String documentNumber) {}

  final RestClient crs;
  final RestClient mdm;
  final String hotel;

  public DemoScanner(@Value("${frontoffice.crs-integration-url:}") String crsIntegrationUrl,
                     @Value("${frontoffice.mdm-url:}") String mdmUrl, @Value("${frontoffice.hotel:MRU01}") String hotel) {
    this.crs = client(crsIntegrationUrl);
    this.mdm = client(mdmUrl);
    this.hotel = hotel;
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

  public Scanned scan(Pax pax) {
    var booked = booked(pax).orElse(null);
    var first = booked != null && booked.firstName() != null ? booked.firstName() : split(pax.name())[0];
    var last = booked != null && booked.lastName() != null ? booked.lastName() : split(pax.name())[1];
    var age = booked == null ? null : booked.age();
    var nationality = booked == null ? null : booked.nationality();
    var made = DemoDocuments.generate(first, last, nationality, age, pax.arrival());

    var current = pax.currentDocument();
    if (current != null && !current.isBlank() && !Guest.placeholderDocument(current)) {
      var type = booked != null && current.equals(booked.documentNumber()) && booked.documentType() != null
          ? booked.documentType() : DemoDocuments.typeOf(current);
      return new Scanned(first, last, type, current, birthOr(booked, made), made.nationality());
    }
    var known = knownByName(first, last);
    if (known.isPresent()) {
      var k = known.get();
      log.info("Pax {} of {} is {} by name: the scanner reads the document the chain knows", pax.number(), pax.locator(), k.path("id").asText());
      return new Scanned(first, last, orElse(text(k, "documentType"), DemoDocuments.typeOf(text(k, "documentNumber"))),
          text(k, "documentNumber"),
          text(k, "birthDate") == null ? birthOr(booked, made) : LocalDate.parse(text(k, "birthDate")),
          orElse(text(k, "nationality"), made.nationality()));
    }
    if (booked != null && booked.documentNumber() != null && !booked.documentNumber().isBlank()) {
      return new Scanned(first, last, orElse(booked.documentType(), DemoDocuments.typeOf(booked.documentNumber())),
          booked.documentNumber(), birthOr(booked, made), made.nationality());
    }
    return new Scanned(first, last, made.documentType(), made.documentNumber(), birthOr(booked, made), made.nationality());
  }

  /**
   * The demo's second case: the same person — the same name, birth date and nationality the scanner
   * reads for them — hands over a passport the chain has never seen ({@link DemoDocuments#newPassport}).
   * The third, a guest with no document, is just not scanning.
   */
  public Scanned scanNewPassport(Pax pax) {
    var usual = scan(pax);
    // New for this stay: one derived from the person alone would be new only the first time — once the MDM
    // keeps it as theirs, «a new passport» would already be a known document on the next demo or test.
    // The same stay gives the same one, so scanning it again is the same passport.
    var seed = DemoDocuments.seed(usual.firstName(), usual.lastName()) ^ DemoDocuments.hash("stay:" + pax.locator());
    var number = DemoDocuments.newPassport(seed);
    if (number.equals(usual.documentNumber())) {
      number = DemoDocuments.newPassport(~seed);
    }
    return usual.withDocument(DemoDocuments.PASSPORT, number);
  }

  static LocalDate birthOr(Booked booked, Scanned made) {
    return booked != null && booked.birthDate() != null ? booked.birthDate() : made.birthDate();
  }

  /**
   * The pax in the booking: the holder for pax 1; for the others, the room's guest of that name, or the
   * one in the pax's place — the holder left out, as the front office lists the companions.
   */
  Optional<Booked> booked(Pax pax) {
    if (crs == null || pax.locator() == null) {
      return Optional.empty();
    }
    try {
      var r = crs.get().uri("/reservations/{hotel}/{locator}", hotel, pax.locator()).retrieve().body(JsonNode.class);
      if (r == null) {
        return Optional.empty();
      }
      var holder = r.path("holder");
      if (pax.number() <= 1) {
        return Optional.of(person(holder));
      }
      var holderName = key(text(holder, "firstName"), text(holder, "lastName"));
      var companions = new ArrayList<JsonNode>();
      var holderSeen = false;
      for (var room : r.path("rooms")) {
        for (var guest : room.path("guests")) {
          if (!holderSeen && key(text(guest, "firstName"), text(guest, "lastName")).equals(holderName)) {
            holderSeen = true;
            continue;
          }
          companions.add(guest);
        }
      }
      var name = key(split(pax.name())[0], split(pax.name())[1]);
      return companions.stream().filter(g -> key(text(g, "firstName"), text(g, "lastName")).equals(name)).findFirst()
          .or(() -> pax.number() - 2 < companions.size() ? Optional.of(companions.get(pax.number() - 2)) : Optional.empty())
          .map(DemoScanner::person);
    } catch (RuntimeException e) {
      log.info("The booking {} could not be read for the scanner ({}): made up without it", pax.locator(), e.getMessage());
      return Optional.empty();
    }
  }

  static Booked person(JsonNode p) {
    var age = p.path("age");
    var child = "CHILD".equalsIgnoreCase(text(p, "type"));
    var birth = text(p, "birthDate");
    return new Booked(text(p, "firstName"), text(p, "lastName"), text(p, "nationality"),
        age.isNumber() ? Integer.valueOf(age.asInt()) : child ? Integer.valueOf(8) : null,
        birth == null ? null : LocalDate.parse(birth), text(p, "documentType"), text(p, "documentNumber"));
  }

  /** A customer of the chain with this name who already has a document — the most recently changed. */
  Optional<JsonNode> knownByName(String first, String last) {
    if (mdm == null || (first == null && last == null)) {
      return Optional.empty();
    }
    var name = key(first, last);
    try {
      var found = mdm.get().uri(b -> b.path("/customers").queryParam("q", last == null ? first : last).build())
          .retrieve().body(JsonNode.class);
      if (found == null) {
        return Optional.empty();
      }
      List<JsonNode> matches = new ArrayList<>();
      found.forEach(c -> {
        if (text(c, "documentNumber") != null && key(text(c, "firstName"), text(c, "lastName")).equals(name)) {
          matches.add(c);
        }
      });
      return matches.stream().findFirst();
    } catch (RuntimeException e) {
      log.info("The MDM could not be asked for {} ({}): the scanner makes the document up", name, e.getMessage());
      return Optional.empty();
    }
  }

  /** First word the name, the rest the surnames. */
  static String[] split(String name) {
    var parts = (name == null ? "" : name.trim().replaceAll("\\s+", " ")).split(" ", 2);
    return new String[] {parts[0].isEmpty() ? null : parts[0], parts.length > 1 ? parts[1] : null};
  }

  static String key(String first, String last) {
    return java.text.Normalizer.normalize((first == null ? "" : first) + " " + (last == null ? "" : last),
            java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
  }

  static String text(JsonNode node, String field) {
    var value = node.path(field);
    return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
  }

  static String orElse(String value, String otherwise) {
    return value == null || value.isBlank() ? otherwise : value;
  }
}
