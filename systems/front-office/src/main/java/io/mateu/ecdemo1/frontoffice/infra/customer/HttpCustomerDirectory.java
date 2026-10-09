package io.mateu.ecdemo1.frontoffice.infra.customer;

import io.mateu.ecdemo1.frontoffice.domain.customer.CustomerDirectory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The chain's MDM as the desk asks it who a person is ({@code frontoffice.mdm-url}): {@code GET
 * /identities/lookup} — 200 the customer, 404 nobody, 409 more than one — and {@code GET
 * /identities/candidates}. Short timeouts; an MDM that does not answer, or is not configured, is
 * UNAVAILABLE or no candidates — never an error the check-in sees.
 */
@Slf4j
@Component
public class HttpCustomerDirectory implements CustomerDirectory {

  final RestClient quick;
  final RestClient writes;

  public HttpCustomerDirectory(@Value("${frontoffice.mdm-url:}") String mdmUrl) {
    this.quick = Http.quick(mdmUrl);
    this.writes = Http.writes(mdmUrl);
  }

  @Override
  public Lookup lookup(LookupQuery q) {
    if (quick == null || q == null) {
      return Lookup.unavailable();
    }
    try {
      return quick.get().uri(b -> {
        b.path("/identities/lookup");
        if (q.documentNumber() != null) {
          b.queryParam("documentNumber", q.documentNumber());
          if (q.country() != null && !q.country().isBlank()) {
            b.queryParam("country", q.country());
          }
        } else if (q.email() != null) {
          b.queryParam("email", q.email());
        } else {
          b.queryParam("riuClass", q.riuClass());
        }
        return b.build();
      }).exchange((request, response) -> {
        var status = response.getStatusCode().value();
        if (status == 404) {
          return Lookup.none();
        }
        if (status == 409) {
          Map<?, ?> body = response.bodyTo(Map.class);
          return Lookup.ambiguous(Http.text(body, "matchedBy"), Http.integer(body, "count"));
        }
        if (status / 100 == 2) {
          Map<?, ?> body = response.bodyTo(Map.class);
          if (Http.text(body, "customerId") == null) {
            return Lookup.unavailable();
          }
          return Lookup.found(new Customer(Http.text(body, "customerId"), Http.text(body, "status"),
              Http.text(body, "firstName"), Http.text(body, "lastName"), Http.date(body, "birthDate")),
              Http.text(body, "matchedBy"));
        }
        log.info("The MDM answered {} to a lookup: taken as unavailable", status);
        return Lookup.unavailable();
      });
    } catch (RuntimeException e) {
      log.info("The MDM could not be asked who a pax is ({}): the desk goes on without it", e.getMessage());
      return Lookup.unavailable();
    }
  }

  @Override
  public List<Candidate> candidates(String firstName, String lastName, LocalDate birthDate, String nationality) {
    if (quick == null || birthDate == null) {
      return List.of();
    }
    try {
      List<?> found = quick.get().uri(b -> {
        b.path("/identities/candidates").queryParam("birthDate", birthDate.toString());
        if (firstName != null) b.queryParam("firstName", firstName);
        if (lastName != null) b.queryParam("lastName", lastName);
        if (nationality != null && !nationality.isBlank()) b.queryParam("nationality", nationality);
        return b.build();
      }).retrieve().body(List.class);
      var candidates = new ArrayList<Candidate>();
      for (var item : found == null ? List.of() : found) {
        if (item instanceof Map<?, ?> c && Http.text(c, "customerId") != null) {
          candidates.add(new Candidate(Http.text(c, "customerId"), Http.text(c, "status"), Http.text(c, "firstName"),
              Http.text(c, "lastName"), Http.date(c, "birthDate"), Http.text(c, "nationality"),
              Http.list(c, "matched").stream().map(String::valueOf).toList()));
        }
      }
      return candidates.stream().limit(5).toList();
    } catch (RuntimeException e) {
      log.info("The MDM could not be asked for candidates ({}): none", e.getMessage());
      return List.of();
    }
  }

  @Override
  public boolean addDocument(String customerId, String type, String number, String issuingCountry, LocalDate expiry,
                             String origin, LocalDate birthDate, String nationality) {
    if (writes == null) {
      return false;
    }
    var body = new LinkedHashMap<String, Object>();
    body.put("type", type);
    body.put("number", number);
    body.put("issuingCountry", issuingCountry);
    body.put("expiry", expiry == null ? null : expiry.toString());
    body.put("origin", origin);
    body.put("birthDate", birthDate == null ? null : birthDate.toString());
    body.put("nationality", nationality);
    try {
      writes.post().uri("/customers/{id}/documents", customerId).contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
      return true;
    } catch (RuntimeException e) {
      log.warn("The MDM did not take the document of {} ({})", customerId, e.getMessage());
      return false;
    }
  }

  @Override
  public boolean setRiuClass(String customerId, String memberNumber) {
    if (writes == null) {
      return false;
    }
    try {
      writes.put().uri("/customers/{id}/xrefs", customerId)
          .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
          .body(Map.of("target", "RIU_CLASS", "reference", memberNumber, "context", "demo"))
          .retrieve().toBodilessEntity();
      return true;
    } catch (RuntimeException e) {
      log.warn("The MDM did not take the Riu Class number of {} ({})", customerId, e.getMessage());
      return false;
    }
  }
}
