package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.folio.FolioRepository;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInChecklist;
import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInOps;
import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInOpsRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** What the screens read of a stay: the stay with its guest and folio, and where its check-in stands. */
@Service
public class StayQueries {

  final StayRepository stays;
  final GuestRepository guests;
  final FolioRepository folios;
  final CheckInOpsRepository checkInOps;

  public StayQueries(StayRepository stays, GuestRepository guests, FolioRepository folios,
                     CheckInOpsRepository checkInOps) {
    this.stays = stays;
    this.guests = guests;
    this.folios = folios;
    this.checkInOps = checkInOps;
  }

  /**
   * The stay behind a screen's route, with its guest and folio. A stale or unknown id falls back to
   * the first stay, as the screens always did.
   */
  public StayView view(String stayId) {
    var stay = stays.findById(stayId == null ? "" : stayId)
        .orElseGet(() -> stays.findAll().stream().findFirst()
            .orElseThrow(() -> new NoSuchElementException("No stays")));
    var guest = guests.findById(stay.guestId()).orElseThrow();
    return new StayView(stay, guest, folios.findByStayId(stay.id()).orElse(null));
  }

  public Optional<Stay> find(String stayId) {
    return stayId == null ? Optional.empty() : stays.findById(stayId);
  }

  public Stay stay(String stayId) {
    return stays.findById(stayId).orElseThrow(() -> new NoSuchElementException("Reserva " + stayId + " no encontrada"));
  }

  /**
   * The next arrival of the stay's group still to check in, if the stay is a group's. The group is
   * SIMULATED: the first word of the agency ("TUI Deutschland" and "TUI Group · …" share TUI).
   */
  public Optional<Stay> nextArrivalOfGroup(Stay done) {
    var group = groupOf(done);
    if (group == null) {
      return Optional.empty();
    }
    return stays.findArrivals().stream()
        .filter(s -> !s.id().equals(done.id()) && group.equals(groupOf(s)))
        .findFirst();
  }

  /** The simulated group of a reservation: the first word of its agency; none without one. */
  public static String groupOf(Stay stay) {
    if (stay.agency() == null || stay.agency().isBlank()) {
      return null;
    }
    return stay.agency().trim().split("\\s+")[0];
  }

  public CheckInOps ops(String stayId) {
    return checkInOps.of(stayId);
  }

  /** How many of the stay's pax still lack a verified identity. */
  public int pendingPax(Stay stay) {
    return CheckInChecklist.pendingPax(stay, guests.findById(stay.guestId()).orElse(null), ops(stay.id()));
  }

  /** The next pax after {@code after} still lacking a verified identity (wrapping round), 0 when none. */
  public int nextPendingPax(String stayId, int after) {
    var stay = stay(stayId);
    return CheckInChecklist.nextPendingPax(stay, guests.findById(stay.guestId()).orElse(null), ops(stay.id()), after);
  }

  /** Whether the desk can check the stay in with no question left to ask — the registration rules' data included. */
  public boolean readyForDirectCheckIn(Stay stay) {
    return CheckInChecklist.readyForDirectCheckIn(stay, guests.findById(stay.guestId()).orElse(null),
        ops(stay.id())) && (registration == null || registration.missing(stay).isEmpty());
  }

  RegistrationRequirementsService registration;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  public void setRegistration(RegistrationRequirementsService registration) {
    this.registration = registration;
  }
}
