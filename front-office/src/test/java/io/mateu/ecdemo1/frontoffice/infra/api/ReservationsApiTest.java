package io.mateu.ecdemo1.frontoffice.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** The reservations the integration writes, as a stay to arrive and a guest of the cardex. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:reservations-api;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@AutoConfigureMockMvc
class ReservationsApiTest {

  @Autowired MockMvc mvc;
  @Autowired StayRepository stays;
  @Autowired GuestRepository guests;

  static String reservation(String roomType, String checkOut) {
    return """
        {"holder":{"customerId":"C-ANA","name":"Ana García","document":null,"email":"ana@example.com","phone":null},
         "companions":[{"customerId":"C-LEO","name":"Leo García"}],
         "roomType":"%s","board":"Solo alojamiento","checkIn":"2026-11-10","checkOut":"%s","pax":2,
         "agency":"ABREU ONLINE PORTUGAL","total":720.00}""".formatted(roomType, checkOut);
  }

  @Test
  void aReservationBecomesAStayToArriveAndItsHolderAGuest() throws Exception {
    mvc.perform(put("/api/reservations/RES1").contentType(MediaType.APPLICATION_JSON)
            .content(reservation("Suite Junior Standard Balcón", "2026-11-13")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.guestId").value("C-ANA"))
        .andExpect(jsonPath("$.status").value("ARRIVING"))
        .andExpect(jsonPath("$.created").value(true));
    var stay = stays.findById("RES1").orElseThrow();
    assertThat(stay.roomNumber()).isNull();
    assertThat(stay.roomLabel()).isEqualTo("Sin asignar");
    assertThat(stay.companions()).extracting(c -> c.name()).containsExactly("Leo García");
    assertThat(guests.findById("C-ANA").orElseThrow().email()).isEqualTo("ana@example.com");

    // Written again — a retried step — one stay, changed as the reservation says.
    mvc.perform(put("/api/reservations/RES1").contentType(MediaType.APPLICATION_JSON)
            .content(reservation("Suite Junior Mar Balcón", "2026-11-14")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(false));
    assertThat(stays.findById("RES1").orElseThrow().roomType()).isEqualTo("Suite Junior Mar Balcón");
    assertThat(stays.findAll().stream().filter(s -> s.id().equals("RES1"))).hasSize(1);
  }

  @Test
  void aChangeFromTheCrsDoesNotUndoWhatTheDeskDid() throws Exception {
    mvc.perform(put("/api/reservations/RES2").contentType(MediaType.APPLICATION_JSON)
        .content(reservation("Suite Junior Standard Balcón", "2026-11-13"))).andExpect(status().isOk());
    stays.save(stays.findById("RES2").orElseThrow().assignRoom("1204", "Suite Junior Standard Balcón").completeCheckIn());

    mvc.perform(put("/api/reservations/RES2").contentType(MediaType.APPLICATION_JSON)
        .content(reservation("Suite Junior Standard Balcón", "2026-11-15"))).andExpect(status().isOk());
    var stay = stays.findById("RES2").orElseThrow();
    assertThat(stay.status()).isEqualTo(StayStatus.IN_HOUSE);
    assertThat(stay.roomNumber()).isEqualTo("1204");
    assertThat(stay.checkOut()).hasToString("2026-11-15");

    // In the house, a cancellation from the CRS is the desk's to settle, not the integration's.
    mvc.perform(post("/api/reservations/RES2/cancellation")).andExpect(status().isConflict());
  }

  @Test
  void aCancelledReservationCancelsTheStayStillToArrive() throws Exception {
    mvc.perform(put("/api/reservations/RES3").contentType(MediaType.APPLICATION_JSON)
        .content(reservation("Suite Junior Standard Balcón", "2026-11-13"))).andExpect(status().isOk());
    mvc.perform(post("/api/reservations/RES3/cancellation"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    // Twice is once.
    mvc.perform(post("/api/reservations/RES3/cancellation")).andExpect(status().isOk());
    assertThat(stays.findById("RES3").orElseThrow().occupies()).isFalse();
  }
}
