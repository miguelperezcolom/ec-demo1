package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestTier;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ten demo reservations for the desk to play with, spread over the day: four arrivals today, one
 * tomorrow, three guests in the house (one leaving today) and two gone. All of them or none.
 */
@Service
public class DemoReservationsService {

  record Template(String name, GuestTier tier) {}

  static final List<Template> TEMPLATES = List.of(
      new Template("Lucía Ortega", GuestTier.GOLD),
      new Template("Marc Vidal", GuestTier.SILVER),
      new Template("Chiara Rossi", GuestTier.PLATINUM),
      new Template("Tom Becker", GuestTier.SILVER),
      new Template("Aiko Tanaka", GuestTier.GOLD),
      new Template("Pierre Dubois", GuestTier.SILVER),
      new Template("Helena Costa", GuestTier.GOLD),
      new Template("Omar Haddad", GuestTier.SILVER),
      new Template("Ingrid Larsen", GuestTier.PLATINUM),
      new Template("Diego Ramírez", GuestTier.SILVER));

  static final List<String> ROOM_TYPES = List.of("Standard", "Deluxe King", "Junior Suite", "Premium Sea View");
  static final List<String> BOARDS = List.of("Solo alojamiento", "Alojamiento y desayuno", "Media pensión");

  final GuestRepository guests;
  final StayRepository stays;

  public DemoReservationsService(GuestRepository guests, StayRepository stays) {
    this.guests = guests;
    this.stays = stays;
  }

  /** @return how many reservations it created */
  @Transactional
  public int seed() {
    var today = LocalDate.now();
    var stamp = String.valueOf(System.currentTimeMillis() % 1_000_000);
    for (int i = 0; i < TEMPLATES.size(); i++) {
      var t = TEMPLATES.get(i);
      var id = "demo-" + stamp + "-" + i;
      guests.save(new Guest(id, t.name(), "D" + stamp + i, true, null, null, t.tier(),
          1000 + i * 500, 1 + i % 5, 4 + i, 1 + i % 3, 0, 1, null, null, List.of()));
      var status = i < 5 ? StayStatus.ARRIVING : i < 8 ? StayStatus.IN_HOUSE : StayStatus.DEPARTED;
      var checkIn = switch (status) {
        case ARRIVING -> i == 4 ? today.plusDays(1) : today;
        case IN_HOUSE -> today.minusDays(1 + i % 3);
        default -> today.minusDays(4 + i % 2);
      };
      var checkOut = switch (status) {
        case ARRIVING -> checkIn.plusDays(2 + i % 4);
        case IN_HOUSE -> i == 5 ? today : today.plusDays(1 + i % 3);
        default -> today.minusDays(i % 2);
      };
      stays.save(new Stay(id, id, String.valueOf(200 + i * 7), ROOM_TYPES.get(i % ROOM_TYPES.size()),
          BOARDS.get(i % BOARDS.size()), checkIn, checkOut, 1 + i % 3, i % 2 == 0 ? "Directo · Web" : "Booking.com",
          new BigDecimal(300 + i * 85), status, 0, 0, null, List.of(), List.of(), Set.of()));
    }
    return TEMPLATES.size();
  }
}
