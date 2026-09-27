package io.mateu.ecdemo1.frontoffice.infra.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayReadModel;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The read model answers what the screens used to compute from the aggregates — the same rows, the
 * same counters, the same occupancy — with a handful of queries instead of one per stay and
 * collection. Checked against the aggregates themselves, on the seeded data.
 */
@SpringBootTest
class StayReadModelTest {

  @Autowired StayReadModel reads;
  @Autowired StayRepository stays;
  @Autowired GuestRepository guests;
  @Autowired RoomRepository rooms;

  @Test
  void rowsAreTheStaysWithTheirGuests() {
    var expected = stays.findAll().stream()
        .map(s -> {
          var guest = guests.findById(s.guestId()).orElseThrow();
          return new StayReadModel.StayRow(s.id(), s.status(), s.checkIn(), s.checkOut(),
              s.roomNumber(), s.roomType(), guest.name(), guest.tier().name());
        })
        .sorted(Comparator.comparing(StayReadModel.StayRow::id))
        .toList();
    var actual = reads.rows().stream().sorted(Comparator.comparing(StayReadModel.StayRow::id)).toList();
    assertEquals(expected, actual);
  }

  @Test
  void theDaysCountersMatchTheAggregates() {
    var today = LocalDate.now();
    var all = stays.findAll();
    var arrivals = all.stream().filter(s -> s.status() == StayStatus.ARRIVING && !s.checkIn().isAfter(today)).count();
    var inHouse = all.stream().filter(s -> s.status() == StayStatus.IN_HOUSE).count();
    var departures = all.stream()
        .filter(s -> (s.status() == StayStatus.IN_HOUSE || s.status() == StayStatus.DEPARTED) && s.checkOut().isEqual(today))
        .count();
    assertEquals(new StayReadModel.Today(arrivals, inHouse, departures), reads.today(today));
  }

  @Test
  void occupancyMatchesTheAggregatesNightByNight() {
    var today = LocalDate.now();
    var expected = new ArrayList<Long>();
    for (int i = 0; i < 7; i++) {
      var night = today.plusDays(i);
      expected.add(stays.findAll().stream()
          .filter(Stay::occupies)
          .filter(s -> !s.checkIn().isAfter(night) && s.checkOut().isAfter(night))
          .count());
    }
    assertEquals(expected, reads.occupiedNights(today, 7));
    assertEquals(rooms.findAll().size(), reads.rooms());
  }
}
