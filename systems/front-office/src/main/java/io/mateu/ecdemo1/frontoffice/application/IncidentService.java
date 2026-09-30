package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.stay.Incident;
import io.mateu.ecdemo1.frontoffice.domain.stay.IncidentStatus;
import io.mateu.ecdemo1.frontoffice.domain.stay.IncidentType;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The incidents of a stay: opened at the desk, resolved by it. */
@Service
public class IncidentService {

  final StayRepository stays;
  final Clock clock = Clock.systemDefaultZone();
  final StayAudit audit;

  public IncidentService(StayRepository stays, StayAudit audit) {
    this.stays = stays;
    this.audit = audit;
  }

  /**
   * Opens an incident of {@code type}. Without a title it is named after its type; without a
   * comment, it says it was reported at the desk.
   */
  @Transactional
  public Incident report(String stayId, IncidentType type, String title, String comment) {
    return audit.run("Incident reported", stayId, null, StayAudit.params("type", type, "title", title),
        () -> open(stayId, type, title, comment), i -> "Incidencia: " + i.title());
  }

  Incident open(String stayId, IncidentType type, String title, String comment) {
    var stay = stays.findById(stayId).orElseThrow(() -> new NoSuchElementException("No stay " + stayId));
    var now = LocalDateTime.now(clock);
    var incident = new Incident("inc-" + System.currentTimeMillis(), type, type.icon(),
        blank(title) ? "Incidencia de " + type.label().toLowerCase(Locale.forLanguageTag("es")) : title,
        blank(comment) ? "Reportada en recepción" : comment, IncidentStatus.OPEN, false, now, null);
    stays.save(stay.reportIncident(incident));
    return incident;
  }

  @Transactional
  public void resolve(String stayId, String code) {
    audit.run("Incident resolved", stayId, null, StayAudit.params("incident", code), () -> {
      var stay = stays.findById(stayId).orElseThrow(() -> new NoSuchElementException("No stay " + stayId));
      return stays.save(stay.resolveIncident(code));
    }, s -> "Incidencia " + code + " resuelta");
  }

  static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}
