package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.registration.PaxRegistrationData;
import io.mateu.ecdemo1.frontoffice.domain.registration.RegistrationRuleCopies;
import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInOpsRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.Companion;
import io.mateu.ecdemo1.frontoffice.domain.stay.PendingStep;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRequirements;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Field;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Moment;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Role;
import io.mateu.ecdemo1.messaging.Inbox;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The destination's registration rules, applied: which of each pax's data the law requires here (by the
 * hotel's country or the hotel, and the pax's nationality, age and role), what of it the kárdex still
 * lacks, and why. The rules are the control plane's, kept here as it sends them (registration-rules),
 * so they apply with or without the network; the reading is the same one the control plane's «Probar»
 * uses ({@link RegistrationRequirements}).
 *
 * <p>Enforced by the services, not the screens: what is missing is one more step of the check-in
 * ({@link IncompleteCheckIns}) — the check-in is refused, whoever asks (the desk, the reception agent),
 * unless forced with a reason and completed later — and a walk-in's holder must carry what the rules
 * ask of it that the walk-in takes down.
 */
@Service
public class RegistrationRequirementsService {

  static final Logger log = LoggerFactory.getLogger(RegistrationRequirementsService.class);
  public static final String CONSUMER = "registration-rules";

  final RegistrationRuleCopies rules;
  final PaxRegistrationData data;
  final GuestRepository guests;
  final CheckInOpsRepository checkInOps;
  final Inbox inbox;
  final String hotel;
  final String hotelCountry;

  public RegistrationRequirementsService(RegistrationRuleCopies rules, PaxRegistrationData data, GuestRepository guests,
                                         CheckInOpsRepository checkInOps, Inbox inbox,
                                         @Value("${frontoffice.hotel:MRU01}") String hotel,
                                         @Value("${frontoffice.hotel-country:MU}") String hotelCountry) {
    this.rules = rules;
    this.data = data;
    this.guests = guests;
    this.checkInOps = checkInOps;
    this.inbox = inbox;
    this.hotel = hotel;
    this.hotelCountry = hotelCountry;
  }

  /** A rule as the control plane sends it: kept unless the one kept is this version or newer. Once per event. */
  @Transactional
  public boolean take(RegistrationRuleChanged rule) {
    if (rule.eventId() != null && !inbox.firstTime(CONSUMER, rule.eventId())) {
      return false;
    }
    var kept = rules.save(rule);
    if (kept) {
      log.info("Registration rule {} v{} ({} {}, {})", rule.ruleId(), rule.version(), rule.scope(), rule.scopeCode(),
          rule.active() ? "active" : "inactive");
    }
    return kept;
  }

  /** What the rules require of this pax of the stay at that moment. Pax 1 is the holder. */
  public RegistrationRequirements.Result required(Stay stay, int pax, Moment moment) {
    var values = values(stay, pax);
    return RegistrationRequirements.of(rules.all(), guest(pax, moment, stay.checkIn(), values));
  }

  /** What the rules require of a walk-in's holder, from what the walk-in takes down. */
  public RegistrationRequirements.Result requiredOfHolder(String nationality, LocalDate arrival) {
    return RegistrationRequirements.of(rules.all(),
        new RegistrationRequirements.Guest(hotel, hotelCountry, Moment.CHECK_IN, arrival == null ? LocalDate.now() : arrival,
            Role.HOLDER, blank(nationality) ? null : nationality.trim(), null));
  }

  RegistrationRequirements.Guest guest(int pax, Moment moment, LocalDate day, Map<Field, String> values) {
    return new RegistrationRequirements.Guest(hotel, hotelCountry, moment, day == null ? LocalDate.now() : day,
        pax <= 1 ? Role.HOLDER : Role.COMPANION, values.get(Field.NATIONALITY), date(values.get(Field.BIRTH_DATE)));
  }

  /**
   * The pax's registration data: what the kárdex keeps apart ({@link PaxRegistrationData}) and what it
   * already has as the identity — the document number, once verified — and the registration's signature.
   */
  public Map<Field, String> values(Stay stay, int pax) {
    var values = new EnumMap<Field, String>(Field.class);
    values.putAll(data.of(stay.id(), pax));
    String document = null;
    if (pax <= 1) {
      var holder = guests.findById(stay.guestId()).orElse(null);
      if (holder != null && holder.identityComplete() && !Guest.placeholderDocument(holder.document())) {
        document = holder.document();
      }
    } else {
      var companion = stay.companionAt(pax);
      if (companion != null && companion.identityComplete() && !Guest.placeholderDocument(companion.document())) {
        document = companion.document();
      }
    }
    if (document != null) {
      values.putIfAbsent(Field.DOCUMENT_NUMBER, document);
    }
    if (checkInOps.of(stay.id()).firma()) {
      values.put(Field.SIGNATURE, "firmado");
    }
    return values;
  }

  /**
   * What the stay's pax still lack for the rules at check-in, as check-in steps — one per pax with
   * something missing (no-shows aside). The signature is a step of its own already, and a pax without
   * a document at all is already one: those are not said twice.
   */
  public List<PendingStep> missing(Stay stay) {
    var steps = new ArrayList<PendingStep>();
    var ops = checkInOps.of(stay.id());
    for (int pax = 1; pax <= stay.pax(); pax++) {
      if (ops.isNoShow(pax)) {
        continue;
      }
      var values = values(stay, pax);
      var result = RegistrationRequirements.of(rules.all(), guest(pax, Moment.CHECK_IN, stay.checkIn(), values));
      var identity = hasIdentity(stay, pax);
      var missing = result.missing(values).stream()
          .filter(f -> f != Field.SIGNATURE && (f != Field.DOCUMENT_NUMBER || identity))
          .map(RegistrationRequirements::label).toList();
      if (!missing.isEmpty()) {
        steps.add(PendingStep.registration(pax, name(stay, pax), missing, String.join("; ", result.legalBases())));
      }
    }
    return steps;
  }

  boolean hasIdentity(Stay stay, int pax) {
    if (pax <= 1) {
      return guests.findById(stay.guestId()).map(Guest::identityComplete).orElse(false);
    }
    var companion = stay.companionAt(pax);
    return companion != null && companion.identityComplete();
  }

  String name(Stay stay, int pax) {
    if (pax <= 1) {
      return guests.findById(stay.guestId()).map(Guest::name).orElse("el titular");
    }
    var companion = stay.companionAt(pax);
    return companion == null ? Companion.pending(pax).name() : companion.name();
  }

  static LocalDate date(String value) {
    try {
      return blank(value) ? null : LocalDate.parse(value.trim());
    } catch (RuntimeException e) {
      return null;
    }
  }

  static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}
