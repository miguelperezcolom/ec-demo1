package io.mateu.ecdemo1.frontoffice.ui.common;

import io.mateu.ecdemo1.frontoffice.application.StayQueries;
import io.mateu.ecdemo1.frontoffice.application.StayView;
import io.mateu.ecdemo1.frontoffice.domain.catalog.AddOnCatalogItem;
import io.mateu.ecdemo1.frontoffice.domain.catalog.AddOnCatalogRepository;
import io.mateu.ecdemo1.frontoffice.domain.room.Room;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInOps;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayReadModel;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Static, READ-ONLY access to the front office for the view models Mateu builds itself, where
 * nothing can be injected: the check-in wizard's steps (the wizard creates them with {@code new} and
 * Mateu re-creates them from the component state on every request), the shared header and link
 * builders they call ({@link GuestHeaders}, {@link OtherSystems}), and {@code Bienvenida}, whose KPIs
 * are computed in field initializers, before any constructor could hand it a bean.
 *
 * <p>Every screen that is a route of its own is a Spring prototype bean and gets what it needs
 * injected; every write goes through the application services. Nothing here writes.
 */
@Component
public class FrontOffice {

  private static FrontOffice instance;

  private final StayQueries queries;
  private final StayReadModel stayReads;
  private final RoomRepository rooms;
  private final AddOnCatalogRepository addOnCatalog;
  private final io.mateu.ecdemo1.frontoffice.application.GuestNotices notices;
  private final io.mateu.ecdemo1.frontoffice.infra.pms.PmsLinks pmsLinks;
  private final io.mateu.ecdemo1.frontoffice.infra.pms.PmsRooms pmsRooms;
  private final io.mateu.ecdemo1.frontoffice.application.Invoices invoices;
  private final io.mateu.ecdemo1.frontoffice.infra.pms.ChargePostings chargePostings;

  public FrontOffice(StayQueries queries, StayReadModel stayReads, RoomRepository rooms,
                     AddOnCatalogRepository addOnCatalog, io.mateu.ecdemo1.frontoffice.application.GuestNotices notices,
                     io.mateu.ecdemo1.frontoffice.infra.pms.PmsLinks pmsLinks,
                     io.mateu.ecdemo1.frontoffice.infra.pms.PmsRooms pmsRooms,
                     io.mateu.ecdemo1.frontoffice.application.Invoices invoices,
                     io.mateu.ecdemo1.frontoffice.infra.pms.ChargePostings chargePostings) {
    this.chargePostings = chargePostings;
    this.queries = queries;
    this.stayReads = stayReads;
    this.rooms = rooms;
    this.addOnCatalog = addOnCatalog;
    this.notices = notices;
    this.pmsLinks = pmsLinks;
    this.pmsRooms = pmsRooms;
    this.invoices = invoices;
    instance = this;
  }

  /** Where the stay stands in the PMS — «Opera: en casa», «Opera: rechazado — …» —, if it was ever told. */
  public static java.util.Optional<String> pmsState(String stayId) {
    return instance.pmsLinks.stateOf(stayId);
  }

  /** The PMS reservation the stay is, if it is linked to one. */
  public static java.util.Optional<String> pmsReservation(String stayId) {
    return instance.pmsLinks.ofStay(stayId).map(io.mateu.ecdemo1.frontoffice.infra.pms.PmsLinks.Link::pmsReservationId)
        .filter(id -> id != null && !id.isBlank());
  }

  /** Where each charge of the stay's folio stands in the PMS's folio, by line id. */
  public static java.util.Map<String, io.mateu.ecdemo1.frontoffice.infra.pms.ChargePostings.Posting> chargePostings(
      String stayId) {
    return instance.chargePostings.ofStay(stayId);
  }

  /** Whether a room can be given now as Opera has it: vacant and as the property's housekeeping requires. */
  public static boolean ready(Room room) {
    return instance.pmsRooms.ready(room);
  }

  /** Whether the room given to a stay is ready in Opera now (kept a few seconds). */
  public static io.mateu.ecdemo1.frontoffice.infra.pms.PmsRooms.Readiness roomReadiness(String roomNumber) {
    return instance.pmsRooms.readiness(roomNumber);
  }

  /** As {@link #roomReadiness}, asking Opera again now (the desk's «Comprobar»). */
  public static io.mateu.ecdemo1.frontoffice.infra.pms.PmsRooms.Readiness refreshRoom(String roomNumber) {
    return instance.pmsRooms.refresh(roomNumber);
  }

  /** What «Abrir factura» opens for a closed stay: the PMS's document, or the front office's proforma. */
  public static io.mateu.ecdemo1.frontoffice.application.Invoices.Summary invoice(String stayId) {
    return instance.invoices.summary(stayId);
  }

  /** The signed link the desk opens the invoice with, in a tab of its own. */
  public static String invoiceLink(String stayId) {
    return instance.invoices.link(stayId);
  }

  /**
   * The rooms to offer a stay at the check-in: the PMS's of its room type, with Opera's state; the
   * front office's own floor when the PMS's catalogue has none (a front office not fed from a PMS).
   */
  public static List<Room> roomsFor(Stay stay, int fallbackFloor) {
    var fromThePms = instance.pmsRooms.ofType(stay.roomType());
    return fromThePms.isEmpty() ? instance.rooms.findByFloor(fallbackFloor) : fromThePms;
  }

  /** The stay behind a screen's route, with its guest and folio. */
  public static StayView stayView(String stayId) {
    return instance.queries.view(stayId);
  }

  /** Where the stay's check-in operations stand. */
  public static CheckInOps ops(String stayId) {
    return instance.queries.ops(stayId);
  }

  /** How many of the stay's pax still lack a verified identity. */
  public static int pendingPax(Stay stay) {
    return instance.queries.pendingPax(stay);
  }

  /** Stays as screens that only look read them: plain queries, no aggregates. */
  public static StayReadModel stayReads() {
    return instance.stayReads;
  }

  public static List<Room> roomsOnFloor(int floor) {
    return instance.rooms.findByFloor(floor);
  }

  public static Optional<Room> room(String number) {
    return instance.rooms.findByNumber(number);
  }

  /** The reception notices of the stay's guests shown at check-in, pax by pax. */
  public static List<io.mateu.ecdemo1.frontoffice.application.GuestNotices.PaxNotice> checkInNotices(String stayId) {
    return instance.queries.find(stayId)
        .map(s -> instance.notices.forStay(s, io.mateu.ecdemo1.frontoffice.domain.guest.CustomerNotice.Moment.CHECK_IN))
        .orElse(List.of());
  }

  /** Whether the stay's blocking check-in notices are read, as they are now. */
  public static boolean checkInNoticesRead(String stayId) {
    return instance.queries.find(stayId).map(instance.notices::checkInAcknowledged).orElse(true);
  }

  public static List<AddOnCatalogItem> addOns() {
    return instance.addOnCatalog.findAll();
  }
}
