package io.mateu.ecdemo1.frontoffice.infra.crs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIn;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIns;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * The desk's walk-ins. The CRS owns the bookings, so the front office is one more of its channels:
 * it asks the CRS — through its adapter, the CRS integration — what the stay costs; on the guest's
 * yes it opens the stay here at once, so the guest can check in now, and has the CRS book it under the
 * stay's own reference at the quoted price. A CRS that does not answer is asked again until it does;
 * one that refuses — a price that changed since the quote — leaves the stay marked for the desk.
 * The booking comes back down the chain as any other reservation: {@code ReservationsApi} recognises
 * it by that reference.
 */
@Slf4j
@Service
public class WalkInDesk {

  static final Duration OFFER_FOR = Duration.ofMinutes(10);
  static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

  private static WalkInDesk instance;

  final WalkIns walkIns;
  final StayRepository stays;
  final GuestRepository guests;
  final TransactionTemplate transaction;
  /** Everything it writes is already plain — dates as text — so a mapper of its own does. */
  final ObjectMapper json = new ObjectMapper();
  final RestClient crs;
  final String hotel;
  final Clock clock = Clock.systemUTC();
  final SecureRandom random = new SecureRandom();
  volatile Offer offer;
  volatile Instant offerReadAt;

  final io.mateu.ecdemo1.frontoffice.domain.guest.CustomerNationalities nationalities;

  public WalkInDesk(WalkIns walkIns, StayRepository stays, GuestRepository guests, PlatformTransactionManager transactions,
                    io.mateu.ecdemo1.frontoffice.domain.guest.CustomerNationalities nationalities,
                    @Value("${frontoffice.crs-integration-url:}") String crsIntegrationUrl,
                    @Value("${frontoffice.hotel:MRU01}") String hotel) {
    this.nationalities = nationalities;
    this.walkIns = walkIns;
    this.stays = stays;
    this.guests = guests;
    this.transaction = new TransactionTemplate(transactions);
    this.crs = crsIntegrationUrl == null || crsIntegrationUrl.isBlank() ? null
        : RestClient.builder().baseUrl(crsIntegrationUrl).build();
    this.hotel = hotel;
    instance = this;
  }

  /**
   * The walk-in a stay is, if it is one. Static — like {@link #stayIdFor} — for the header and route
   * helpers the Mateu-built wizard steps call, where nothing is injected.
   */
  public static Optional<WalkIn> of(String stayId) {
    return instance == null || stayId == null ? Optional.empty() : instance.walkIns.of(stayId);
  }

  /**
   * The stay a route names: a walk-in keeps the id the desk gave it ({@code FO-…}), so the CRS
   * locator other systems link it by is turned into that id; any other id is left as it is.
   */
  public static String stayIdFor(String id) {
    if (instance == null || id == null || instance.stays.findById(id).isPresent()) {
      return id;
    }
    return instance.walkIns.byLocator(id).map(WalkIn::stayId).orElse(id);
  }

  // ── what the CRS offers, and what it costs ─────────────────────────────────────

  public record Option(String code, String name) {}

  /** The CRS's room types, rate plans and boards for this hotel, in its codes and its words. */
  public record Offer(String hotelCode, String hotelName, List<Option> roomTypes, List<Option> ratePlans,
                      List<Option> boards) {

    public String name(List<Option> options, String code) {
      return options.stream().filter(o -> o.code().equals(code)).map(Option::name).findFirst().orElse(code);
    }
  }

  /** Read again every ten minutes; empty if this front office is not connected to the CRS. */
  public Offer offer() {
    if (crs == null) {
      return new Offer(hotel, hotel, List.of(), List.of(), List.of());
    }
    var now = clock.instant();
    if (offer == null || offerReadAt.plus(OFFER_FOR).isBefore(now)) {
      offer = crs.get().uri(b -> b.path("/walk-ins/offer").queryParam("hotelCode", hotel).build()).retrieve()
          .body(Offer.class);
      offerReadAt = now;
    }
    return offer;
  }

  /** The person at the desk, holder of the booking. */
  public record Holder(String firstName, String lastName, String email, String phone, String nationality,
                       String documentType, String documentNumber) {

    public String fullName() {
      return (firstName == null ? "" : firstName.trim()) + " " + (lastName == null ? "" : lastName.trim());
    }
  }

  /** One room, today on, as the CRS integration takes it. */
  public record Request(String hotelCode, String reference, LocalDate arrival, LocalDate departure,
                        String roomTypeCode, String ratePlanCode, String boardCode, int adults,
                        List<Integer> childrenAges, Holder holder, BigDecimal expectedTotal) {

    public int pax() {
      return adults + (childrenAges == null ? 0 : childrenAges.size());
    }
  }

  /** What the CRS says it costs. */
  public record Quote(String currency, int nights, BigDecimal total) {}

  /** The CRS's price for the stay, or why it will not sell it. */
  public Quote quote(Request request) {
    if (crs == null) {
      throw new IllegalStateException("Este front office no está conectado al CRS: no puede pedir precio.");
    }
    try {
      var answer = crs.post().uri("/walk-ins/quote").contentType(MediaType.APPLICATION_JSON)
          .body(forHotel(request, null, null)).retrieve().body(Map.class);
      return new Quote(String.valueOf(answer.get("currency")), ((Number) answer.get("nights")).intValue(),
          new BigDecimal(String.valueOf(answer.get("total"))));
    } catch (HttpClientErrorException e) {
      throw new IllegalArgumentException("El CRS no la vende así: " + reason(e));
    }
  }

  // ── the stay, now; the booking, as soon as the CRS takes it ────────────────────

  /**
   * Opens the stay for the walk-in — arriving, costing the quote — and sends the booking to the CRS.
   * The stay's id is the booking's reference in the CRS: FO-….
   */
  public WalkIn open(Request request, Quote quote) {
    // One transaction for the guest, the stay and the walk-in; the CRS is asked outside it (send).
    return transaction.execute(status -> opened(request, quote));
  }

  WalkIn opened(Request request, Quote quote) {
    var reference = newReference();
    var offer = offer();
    var holder = request.holder();
    var guestId = "wi-" + reference;
    guests.save(Guest.fromReservation(guestId, holder.fullName().trim(), holder.documentNumber(), holder.email(),
        holder.phone()));
    // the flag next to the holder's name: the nationality the desk took down
    nationalities.put(guestId, holder.nationality(), "WALK_IN");
    stays.save(Stay.fromReservation(reference, guestId, offer.name(offer.roomTypes(), request.roomTypeCode()),
        offer.name(offer.boards(), request.boardCode()), request.arrival(), request.departure(), request.pax(),
        "Walk-in · Recepción", quote.total(), List.of()));
    var walkIn = WalkIn.pending(reference, write(forHotel(request, reference, quote.total())), quote.total(),
        clock.instant());
    walkIns.save(walkIn);
    log.info("{}: walk-in opened for {}, {} {}; to the CRS", reference, holder.fullName(), quote.total(),
        quote.currency());
    return walkIn;
  }

  /** Sends what is still to be sent: right after opening it, and again while the CRS does not answer. */
  @Scheduled(fixedDelayString = "${frontoffice.walk-in-resend:15s}")
  public void resend() {
    walkIns.pending().forEach(this::send);
  }

  public WalkIn send(WalkIn walkIn) {
    if (crs == null || walkIn.status() != WalkIn.WalkInStatus.PENDING) {
      return walkIn;
    }
    try {
      var booked = crs.post().uri("/walk-ins").contentType(MediaType.APPLICATION_JSON).body(walkIn.request())
          .retrieve().body(Map.class);
      var locator = booked == null ? null : (String) booked.get("locator");
      var after = walkIns.of(walkIn.stayId()).orElse(walkIn);
      // The booking may have come back down the chain before this answer: that already said the locator.
      var saved = after.status() == WalkIn.WalkInStatus.BOOKED ? after : after.booked(locator, clock.instant());
      walkIns.save(saved);
      log.info("{}: the CRS booked it as {}", walkIn.stayId(), locator);
      return saved;
    } catch (HttpClientErrorException e) {
      var refused = walkIn.refused(reason(e));
      walkIns.save(refused);
      log.warn("{}: the CRS refused the walk-in — {}", walkIn.stayId(), refused.message());
      return refused;
    } catch (RuntimeException e) {
      log.warn("{}: the CRS did not answer ({}); it goes again", walkIn.stayId(), e.getMessage());
      return walkIn;
    }
  }

  Map<String, Object> forHotel(Request r, String reference, BigDecimal expectedTotal) {
    var body = new java.util.HashMap<String, Object>();
    body.put("hotelCode", hotel);
    body.put("reference", reference);
    body.put("arrival", r.arrival().toString());
    body.put("departure", r.departure().toString());
    body.put("roomTypeCode", r.roomTypeCode());
    body.put("ratePlanCode", r.ratePlanCode());
    body.put("boardCode", r.boardCode());
    body.put("adults", r.adults());
    body.put("childrenAges", r.childrenAges() == null ? List.of() : r.childrenAges());
    body.put("holder", r.holder());
    body.put("expectedTotal", expectedTotal);
    return body;
  }

  String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  /** FO- and six letters or digits no one confuses at the desk (no O/0, I/1). */
  String newReference() {
    String reference;
    do {
      var chars = new char[6];
      for (int i = 0; i < chars.length; i++) {
        chars[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
      }
      reference = "FO-" + new String(chars);
    } while (stays.findById(reference).isPresent());
    return reference;
  }

  static String reason(HttpClientErrorException e) {
    try {
      var detail = e.getResponseBodyAs(ProblemDetail.class);
      if (detail != null && detail.getDetail() != null) {
        return detail.getDetail();
      }
    } catch (RuntimeException ignored) {
      // not a problem detail: the status says it
    }
    return e.getStatusText();
  }
}
