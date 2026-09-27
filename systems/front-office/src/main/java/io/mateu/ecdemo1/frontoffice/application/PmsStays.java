package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.stay.Companion;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIn;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIns;
import io.mateu.ecdemo1.messaging.Inbox;
import io.mateu.ecdemo1.frontoffice.infra.pms.PmsCatalogue;
import io.mateu.ecdemo1.frontoffice.infra.pms.PmsLinks;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.CatalogueType;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.ReplaceCatalogue;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.WriteStay;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The front office consumes the PMS (the chain CRS → PMS → front office): the pms-fo integration
 * tells it each reservation as the PMS holds it, and the PMS's catalogue to read it with. Each command
 * is applied once — its id goes to the inbox in the same transaction — and a stay is written by
 * state, ordered by the PMS's version of the reservation, so nothing is undone by a late or repeated
 * message.
 *
 * <p>What the PMS says replaces what the reservation said; what happened at the hotel — the room, the
 * check-in, the people the desk registered — is the desk's and stays. A reservation the desk already
 * has — a CRS locator, a walk-in the desk opened — is recognised, never opened twice.
 */
@Service
public class PmsStays {

  static final Logger log = LoggerFactory.getLogger(PmsStays.class);
  public static final String ROOM_ONLY = "Solo alojamiento";

  /** What became of a command. */
  public enum Outcome { WRITTEN, CANCELLED, NO_SHOW, KEPT_BY_THE_DESK, STALE, UNKNOWN_STAY, CATALOGUE, DUPLICATE, OTHER_HOTEL }

  final String pmsHotel;
  final StayRepository stays;
  final WalkIns walkIns;
  final StayWrites writes;
  final PmsCatalogue catalogue;
  final PmsLinks links;
  /** Who the inbox knows these commands by; the ids the front office took before are kept under it. */
  public static final String CONSUMER = "front-office";

  final Inbox inbox;
  final Clock clock = Clock.systemUTC();

  public PmsStays(@Value("${frontoffice.pms-hotel:XMAR}") String pmsHotel, StayRepository stays, WalkIns walkIns,
      StayWrites writes, PmsCatalogue catalogue, PmsLinks links, Inbox inbox) {
    this.pmsHotel = pmsHotel;
    this.stays = stays;
    this.walkIns = walkIns;
    this.writes = writes;
    this.catalogue = catalogue;
    this.links = links;
    this.inbox = inbox;
  }

  public String pmsHotel() {
    return pmsHotel;
  }

  /** A command of the pms-fo integration, once. One for another property is not this front office's. */
  @Transactional
  public Outcome take(FrontOfficeCommand command) {
    var hotel = switch (command) {
      case WriteStay w -> w.pmsHotelCode();
      case ReplaceCatalogue c -> c.pmsHotelCode();
    };
    if (!pmsHotel.equals(hotel)) {
      log.debug("Command {} is for PMS property {}, not {}: not this front office's", command.commandId(), hotel, pmsHotel);
      return Outcome.OTHER_HOTEL;
    }
    if (command.commandId() != null && !command.commandId().isBlank()
        && !inbox.firstTime(CONSUMER, command.commandId())) {
      log.debug("Command {} already taken", command.commandId());
      return Outcome.DUPLICATE;
    }
    return switch (command) {
      case WriteStay w -> write(w);
      case ReplaceCatalogue c -> {
        catalogue.replace(c.pmsHotelCode(), c.commandId(), c.entries(), clock.instant());
        log.info("PMS catalogue of {}: {} entries", c.pmsHotelCode(), c.entries() == null ? 0 : c.entries().size());
        yield Outcome.CATALOGUE;
      }
    };
  }

  Outcome write(WriteStay w) {
    var found = stayOf(w);
    var current = found.flatMap(links::ofStay).map(PmsLinks.Link::pmsVersion).orElse(null);
    if (current != null && w.pmsVersion() != null && current.compareTo(w.pmsVersion()) > 0) {
      log.info("{} v{}: the front office already holds v{} of PMS reservation {}, kept", found.get(), w.pmsVersion(),
          current, w.pmsReservationId());
      return Outcome.STALE;
    }
    var status = w.status() == null ? FrontOfficeCommand.PmsStatus.RESERVED : w.status();
    if (status != FrontOfficeCommand.PmsStatus.RESERVED) {
      if (found.isEmpty()) {
        log.info("PMS reservation {} is {} and never was a stay here: nothing to do", w.pmsReservationId(), status);
        return Outcome.UNKNOWN_STAY;
      }
      var stay = stays.findById(found.get()).orElseThrow();
      try {
        stays.save(status == FrontOfficeCommand.PmsStatus.NO_SHOW ? stay.noShow(w.total()) : stay.cancel());
      } catch (IllegalStateException e) {
        log.warn("{}: the PMS has it {}, but the desk keeps its stay — {}", stay.id(), status, e.getMessage());
        links.link(stay.id(), w.pmsReservationId(), w.pmsVersion(), ratePlan(w));
        return Outcome.KEPT_BY_THE_DESK;
      }
      links.link(stay.id(), w.pmsReservationId(), w.pmsVersion(), ratePlan(w));
      log.info("{} {} from the PMS ({})", stay.id(), status == FrontOfficeCommand.PmsStatus.NO_SHOW
          ? "a no-show, costing " + w.total() : "cancelled", w.pmsReservationId());
      return status == FrontOfficeCommand.PmsStatus.NO_SHOW ? Outcome.NO_SHOW : Outcome.CANCELLED;
    }
    var stayId = found.orElseGet(() -> newStayId(w));
    var holder = w.holder();
    var written = writes.write(stayId, new StayWrites.Booking(guestId(w),
            new StayWrites.Holder(holder == null ? "" : holder.name(), holder == null ? null : holder.document(),
                holder == null ? null : holder.email(), holder == null ? null : holder.phone()),
            companions(w), roomType(w), board(w), w.checkIn(), w.checkOut(), Math.max(1, w.pax()), w.agency(), w.total()),
        true);
    links.link(stayId, w.pmsReservationId(), w.pmsVersion(), ratePlan(w));
    walkInOf(w).ifPresent(walkIn -> walkIns.save(walkIn.cameBack(w.crsLocator() != null ? w.crsLocator() : walkIn.locator(),
        w.pmsReservationId(), clock.instant())));
    log.info("PMS reservation {} v{} written onto stay {} ({})", w.pmsReservationId(), w.pmsVersion(), stayId,
        written.created() ? "new" : "changed");
    return Outcome.WRITTEN;
  }

  /**
   * The stay a PMS reservation already is: the one linked to it; the CRS's, by its locator; or the
   * walk-in the desk opened, by the locator the CRS gave it or by the stay id it carries as a reference.
   */
  Optional<String> stayOf(WriteStay w) {
    var linked = links.byPmsReservation(w.pmsReservationId()).map(PmsLinks.Link::stayId);
    if (linked.isPresent()) {
      return linked;
    }
    if (w.crsLocator() != null && stays.findById(w.crsLocator()).isPresent()) {
      return Optional.of(w.crsLocator());
    }
    return walkInOf(w).map(WalkIn::stayId).filter(id -> stays.findById(id).isPresent());
  }

  Optional<WalkIn> walkInOf(WriteStay w) {
    var byLocator = w.crsLocator() == null ? Optional.<WalkIn>empty() : walkIns.byLocator(w.crsLocator());
    if (byLocator.isPresent()) {
      return byLocator;
    }
    for (var ref : w.externalReferences() == null ? List.<String>of() : w.externalReferences()) {
      var walkIn = ref == null ? Optional.<WalkIn>empty() : walkIns.of(ref);
      if (walkIn.isPresent()) {
        return walkIn;
      }
    }
    return Optional.empty();
  }

  /** A stay the front office did not have: the CRS's locator when it came from the CRS, else the PMS's confirmation. */
  static String newStayId(WriteStay w) {
    if (w.crsLocator() != null && !w.crsLocator().isBlank()) {
      return w.crsLocator();
    }
    return "OP-" + (w.confirmationNumber() != null && !w.confirmationNumber().isBlank() ? w.confirmationNumber()
        : w.pmsReservationId());
  }

  /** The chain's customer when the MDM knows the guest; else the PMS's profile, or the reservation itself. */
  static String guestId(WriteStay w) {
    var h = w.holder();
    if (h != null && h.customerId() != null && !h.customerId().isBlank()) {
      return h.customerId();
    }
    if (h != null && h.pmsProfileId() != null && !h.pmsProfileId().isBlank()) {
      return "opera-" + h.pmsProfileId();
    }
    return "pms-" + w.pmsReservationId();
  }

  String roomType(WriteStay w) {
    return catalogue.describe(w.pmsHotelCode(), CatalogueType.ROOM_TYPE, w.roomTypeCode()).orElse(w.roomTypeCode());
  }

  String board(WriteStay w) {
    if (w.boardCode() == null || w.boardCode().isBlank()) {
      return ROOM_ONLY;
    }
    return catalogue.describe(w.pmsHotelCode(), CatalogueType.PACKAGE, w.boardCode()).orElse(w.boardCode());
  }

  String ratePlan(WriteStay w) {
    return catalogue.describe(w.pmsHotelCode(), CatalogueType.RATE_PLAN, w.ratePlanCode()).orElse(w.ratePlanCode());
  }

  /** The room's other people, in pax order: the holder is pax 1 and is not among them. */
  static List<Companion> companions(WriteStay w) {
    var list = w.companions() == null ? List.<FrontOfficeCommand.Person>of() : w.companions();
    var result = new ArrayList<Companion>();
    for (int i = 0; i < list.size(); i++) {
      var p = list.get(i);
      result.add(new Companion(p.customerId() == null ? "pax-" + (i + 2) : p.customerId(), p.name(), p.document(),
          false, p.email(), p.phone(), "Pax " + (i + 2) + " · de la reserva"));
    }
    return result;
  }
}
