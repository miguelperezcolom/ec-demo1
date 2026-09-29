package io.mateu.ecdemo1.frontoffice.infra.pms;

import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIns;
import io.mateu.ecdemo1.frontoffice.infra.outbox.CommandOutbox;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeEvent;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * What the desk did, up to the PMS — the master of the stay (pms-fo). A check-in, a check-out, a
 * reservation nobody came for: each is an event on {@code front-office-events}, written to the outbox
 * in the transaction of the desk's decision, and the pms-fo integration records it in Opera through
 * the engine. The stay shows where that stands: «Opera: pendiente…» now, and what the PMS answers
 * later — «en casa», «salida registrada», «rechazado — motivo» ({@link PmsLinks#state}).
 *
 * <p>The event names the stay both ways: the CRS's locator when the reservation came from the CRS (a
 * walk-in's once the CRS booked it; none for one born in Opera, {@code OP-…}), and Opera's reservation
 * when the stay is linked to it. A walk-in neither has yet is told once its reservation comes back
 * from Opera ({@link #pendingLink}).
 */
@Service
public class ReceptionReports {

  static final Logger log = LoggerFactory.getLogger(ReceptionReports.class);

  /** The stay's state while the PMS does not have its reservation yet (a walk-in the CRS is booking). */
  public static final String WAITING_FOR_THE_PMS = "Opera: pendiente — la reserva aún no ha llegado a Opera";

  final String hotel;
  final String pmsHotel;
  final WalkIns walkIns;
  final PmsLinks links;
  final CommandOutbox outbox;
  final Clock clock = Clock.systemUTC();

  public ReceptionReports(@Value("${frontoffice.hotel:MRU01}") String hotel,
      @Value("${frontoffice.pms-hotel:XMAR}") String pmsHotel, WalkIns walkIns, PmsLinks links, CommandOutbox outbox) {
    this.hotel = hotel;
    this.pmsHotel = pmsHotel;
    this.walkIns = walkIns;
    this.links = links;
    this.outbox = outbox;
  }

  record Refs(String crsLocator, String pmsReservationId) {
    boolean none() {
      return crsLocator == null && pmsReservationId == null;
    }
  }

  Refs refs(String stayId) {
    var walkIn = walkIns.of(stayId).orElse(null);
    String crs;
    if (walkIn != null) {
      crs = walkIn.locator();
    } else {
      crs = stayId.startsWith("OP-") ? null : stayId;
    }
    var pms = links.ofStay(stayId).map(PmsLinks.Link::pmsReservationId).filter(id -> !id.isBlank()).orElse(null);
    if (pms == null && walkIn != null) {
      pms = walkIn.pmsReservationId();
    }
    return new Refs(crs, pms);
  }

  /** The desk checked the stay in: in its transaction, the PMS is asked to record it. */
  public void checkedIn(Stay stay, String by) {
    var refs = refs(stay.id());
    if (refs.none()) {
      links.state(stay.id(), WAITING_FOR_THE_PMS);
      log.info("{}: checked in; neither the CRS nor Opera has its reservation yet — told once it is back", stay.id());
      return;
    }
    outbox.appendEvent(new FrontOfficeEvent.GuestCheckedIn("CI-" + UUID.randomUUID(), clock.instant(), hotel, stay.id(),
        refs.crsLocator(), pmsHotel, refs.pmsReservationId(), stay.roomNumber(), stay.pax(), by));
    links.state(stay.id(), "Opera: pendiente — check-in enviado");
    log.info("{}: checked in, room {} — to the PMS ({} / {})", stay.id(), stay.roomNumber(), refs.crsLocator(),
        refs.pmsReservationId());
  }

  /** The desk checked the stay out: in its transaction, the PMS is asked to record it. */
  public void checkedOut(Stay stay, String by) {
    var refs = refs(stay.id());
    if (refs.none()) {
      log.info("{}: checked out; its reservation is not in the PMS — nothing to record there", stay.id());
      return;
    }
    outbox.appendEvent(new FrontOfficeEvent.GuestCheckedOut("CO-" + UUID.randomUUID(), clock.instant(), hotel, stay.id(),
        refs.crsLocator(), pmsHotel, refs.pmsReservationId(), stay.roomNumber(), by));
    links.state(stay.id(), "Opera: pendiente — check-out enviado");
    log.info("{}: checked out — to the PMS ({} / {})", stay.id(), refs.crsLocator(), refs.pmsReservationId());
  }

  /**
   * Nobody of the stay's reservation came: in the caller's transaction, the PMS is asked to record it;
   * it reports it to the CRS, which applies its fee. What to tell the desk.
   */
  public String noShow(String stayId, int pax, String by) {
    var walkIn = walkIns.of(stayId).orElse(null);
    if (walkIn != null && walkIn.locator() == null) {
      return "Este walk-in aún no está en el CRS: el no show queda solo aquí.";
    }
    var refs = refs(stayId);
    outbox.appendEvent(new FrontOfficeEvent.NoShowReported("NS-" + UUID.randomUUID(), clock.instant(), hotel, stayId,
        refs.crsLocator(), pmsHotel, refs.pmsReservationId(), pax, by));
    links.state(stayId, "Opera: pendiente — no show enviado");
    log.info("{}: a no-show — to the PMS, and from it to the CRS ({} / {})", stayId, refs.crsLocator(), refs.pmsReservationId());
    return "Se comunica a Opera, el PMS, que lo anota y lo sube al CRS: si la reserva es suya, la cancela con su cargo "
        + "de no show y la estancia lo mostrará.";
  }

  /**
   * A stay the desk checked in before its reservation was anywhere — a walk-in — now linked to it: its
   * check-in goes up now. Nothing for any other stay.
   */
  public void pendingLink(Stay stay) {
    if (stay.inHouse() && WAITING_FOR_THE_PMS.equals(links.stateOf(stay.id()).orElse(null))) {
      checkedIn(stay, "front office " + hotel);
    }
  }
}
