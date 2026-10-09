package io.mateu.ecdemo1.frontoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.guest.PaxKardexes.PaxKardex;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.infra.outbox.CommandOutbox;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Field;
import io.mateu.ecdemo1.messaging.OutboxMessage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The kárdex the desk fills in with the guest: kept (no longer provisional), and sent to the chain's MDM
 * with the registration data the desk wrote — the guest's own declaration.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:kardex-filled;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
    "frontoffice.arrivals-briefing.enabled=false"})
class KardexFilledTest {

  @Autowired KardexService kardex;
  @Autowired GuestRepository guests;
  @Autowired StayRepository stays;
  @Autowired CommandOutbox outbox;

  @Test
  void theKardexIsKeptAndGoesToTheMdmWithTheRegistrationData() {
    guests.save(Guest.fromReservation("C-KF1", "Ana María García López", "X1234567", "ana@example.com", null));
    stays.save(Stay.fromReservation("KF-1", "C-KF1", "Doble", "Desayuno", LocalDate.now(), LocalDate.now().plusDays(3),
        1, "TUI", new BigDecimal("300.00"), List.of()));
    assertThat(kardex.kardexOf("KF-1", 1)).isEmpty();

    var data = new EnumMap<Field, String>(Field.class);
    data.put(Field.SEX, "F");
    data.put(Field.ADDRESS, "Calle Mayor 1");
    data.put(Field.CITY, "Palma");
    data.put(Field.POSTAL_CODE, "07001");
    data.put(Field.COUNTRY_OF_RESIDENCE, "ES");
    data.put(Field.BIRTH_DATE, "1990-05-17");
    data.put(Field.DOCUMENT_TYPE, "PASSPORT");
    data.put(Field.DOCUMENT_EXPIRY, "2031-03-01");
    kardex.registrationData("KF-1", 1, data);
    kardex.kardexFilled("KF-1", 1, new PaxKardex("KF-1", 1, "Ana María", "García López", "RC00000042",
        LocalDate.of(2021, 3, 2), "es", "Illes Balears", "+34971000000", true, null, null), "recepción");

    assertThat(kardex.kardexOf("KF-1", 1)).get().satisfies(k -> {
      assertThat(k.lastName()).isEqualTo("García López");
      assertThat(k.filledAt()).isNotNull();
      assertThat(k.marketingConsent()).isTrue();
    });
    var sent = outbox.all(CommandOutbox.CUSTOMER_COMMANDS).stream().map(OutboxMessage::payload)
        .filter(p -> p.contains("record-kardex") && p.contains("KF-1")).toList();
    assertThat(sent).singleElement().satisfies(p -> assertThat(p)
        .contains("\"customerId\":\"C-KF1\"").contains("\"firstName\":\"Ana María\"").contains("\"sex\":\"F\"")
        .contains("\"province\":\"Illes Balears\"").contains("\"documentNumber\":\"X1234567\"")
        .contains("\"documentIssueDate\":\"2021-03-02\"").contains("\"riuClass\":\"RC00000042\"")
        .contains("\"marketingConsent\":true").contains("\"companion\":false"));
  }
}
