package io.mateu.ecdemo1.pmsintegration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integration.model.mapping.Translation;
import io.mateu.ecdemo1.integration.model.partner.BillingMode;
import io.mateu.ecdemo1.integration.model.partner.Partner;
import io.mateu.ecdemo1.integration.model.partner.PartnerType;
import io.mateu.ecdemo1.integration.model.reservation.GuestType;
import io.mateu.ecdemo1.integration.model.reservation.NightlyRate;
import io.mateu.ecdemo1.integration.model.reservation.Payment;
import io.mateu.ecdemo1.integration.model.reservation.PaymentType;
import io.mateu.ecdemo1.integration.model.reservation.Person;
import io.mateu.ecdemo1.integration.model.reservation.Reservation;
import io.mateu.ecdemo1.integration.model.reservation.ReservationStatus;
import io.mateu.ecdemo1.integration.model.reservation.Room;
import io.mateu.ecdemo1.pmsintegration.clients.IntegrationClients;
import io.mateu.ecdemo1.pmsintegration.config.OhipProperties;
import io.mateu.ecdemo1.pmsintegration.write.ReservationPayload;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** What an integration reservation becomes on the wire to OHIP. */
class ReservationPayloadTest {

    final ReservationPayload payload = new ReservationPayload(new ObjectMapper(),
            new OhipProperties("u", "a", "c", "s", "RIUE", "RIUCRS", List.of("RIUPMI"), "CRS_VERSION", "CA", Duration.ofSeconds(5)));

    static final LocalDate IN = LocalDate.of(2026, 10, 9);

    static Reservation reservation(List<Payment> payments, String board) {
        var rates = List.of(new NightlyRate(IN, new BigDecimal("147.90")), new NightlyRate(IN.plusDays(1), new BigDecimal("133.50")));
        return new Reservation("PMI01", "LOC1", 3, ReservationStatus.CONFIRMED, "TTOO", "NORDTRAVEL", "NT-42", IN, IN.plusDays(2),
                "EUR", new Person("Ana", "García", GuestType.ADULT, null, "ana@example.com", null, "ES", null, null, null),
                List.of(new Room(1, "DBL", "TTOO", board, 2, List.of(7), List.of(), rates)), payments,
                new BigDecimal("281.40"), "Late arrival", null);
    }

    static IntegrationClients.Resolved codes() {
        return new IntegrationClients.Resolved(List.of(
                new Translation(CodeType.CHANNEL, "TTOO", "TOUROP", Map.of("marketCode", "TOUR")),
                new Translation(CodeType.ROOM_TYPE, "DBL", "STDK", Map.of()),
                new Translation(CodeType.RATE_PLAN, "TTOO", "TOPKG", Map.of()),
                new Translation(CodeType.BOARD, "AD", "BKFST", Map.of()),
                new Translation(CodeType.BOARD, "SA", "NONE", Map.of()),
                new Translation(CodeType.PAYMENT_METHOD, "VISA", "VA", Map.of())), List.of());
    }

    static Partner partner(BillingMode mode) {
        return new Partner("NORDTRAVEL", PartnerType.TOUR_OPERATOR, "Nordic Travel", null, null, null, null, mode, true, 1);
    }

    @Test
    void everyCodeIsTheTranslatedOneAndThePriceIsFixedNightByNight() {
        var r = payload.build(reservation(List.of(), "AD"), codes(), "RIUPMI", "G1", partner(BillingMode.FRONT), "P1", "Agent")
                .path("reservations").path("reservation").get(0);
        var rate = r.path("roomStay").path("roomRates").get(0);

        assertThat(r.path("hotelId").asText()).isEqualTo("RIUPMI");
        assertThat(rate.path("roomType").asText()).isEqualTo("STDK");
        assertThat(rate.path("ratePlanCode").asText()).isEqualTo("TOPKG");
        assertThat(rate.path("sourceCode").asText()).isEqualTo("TOUROP");
        assertThat(rate.path("marketCode").asText()).isEqualTo("TOUR");
        assertThat(rate.path("fixedRate").asBoolean()).isTrue();
        assertThat(rate.path("guestCounts").path("childAges").get(0).asInt()).isEqualTo(7);
        assertThat(rate.path("rates").path("rate")).hasSize(2);
        assertThat(rate.path("rates").path("rate").get(1).path("start").asText()).isEqualTo("2026-10-10");
        assertThat(rate.path("rates").path("rate").get(1).path("base").path("amountBeforeTax").decimalValue()).isEqualByComparingTo("133.50");
        assertThat(rate.path("total").path("amountBeforeTax").decimalValue()).isEqualByComparingTo("281.40");
        assertThat(r.path("reservationPackages").get(0).path("packageCode").asText()).isEqualTo("BKFST");
    }

    @Test
    void itCarriesTheLocatorTheVoucherTheVersionAndTheProfiles() {
        var r = payload.build(reservation(List.of(), "AD"), codes(), "RIUPMI", "G1", partner(BillingMode.FRONT), "P1", "Agent")
                .path("reservations").path("reservation").get(0);

        assertThat(r.path("externalReferences").toString()).contains("\"id\":\"LOC1\",\"idContext\":\"RIUCRS\"")
                .contains("\"id\":\"NT-42\",\"idContext\":\"NORDTRAVEL\"");
        assertThat(r.path("userDefinedFields").path("numericUDFs").get(0).path("name").asText()).isEqualTo("CRS_VERSION");
        assertThat(r.path("userDefinedFields").path("numericUDFs").get(0).path("value").asLong()).isEqualTo(3);
        assertThat(r.path("reservationGuests").get(0).path("profileInfo").path("profileIdList").get(0).path("id").asText()).isEqualTo("G1");
        assertThat(r.path("reservationProfiles").path("reservationProfile").get(0).path("reservationProfileType").asText())
                .isEqualTo("TravelAgent");
    }

    @Test
    void thePartnerWindowGetsTheStayOnlyWhenThePartnerPays() {
        var front = payload.build(reservation(List.of(), "AD"), codes(), "RIUPMI", "G1", partner(BillingMode.FRONT), "P1", "Agent");
        var noFront = payload.build(reservation(List.of(), "AD"), codes(), "RIUPMI", "G1", partner(BillingMode.NO_FRONT), "P1", "Agent");

        assertThat(front.path("reservations").path("reservation").get(0).has("routingInstructions")).isFalse();
        var routing = noFront.path("reservations").path("reservation").get(0).path("routingInstructions").get(0);
        assertThat(routing.path("folioWindowNo").asInt()).isEqualTo(2);
        assertThat(routing.path("profileId").asText()).isEqualTo("P1");
    }

    @Test
    void roomOnlyIsNoPackageAndAReservationWithNoPaymentGoesAsPayAtHotel() {
        var r = payload.build(reservation(List.of(), "SA"), codes(), "RIUPMI", "G1", null, null, null)
                .path("reservations").path("reservation").get(0);

        assertThat(r.path("reservationPackages")).isEmpty();
        assertThat(r.path("reservationPaymentMethods").get(0).path("paymentMethod").asText()).isEqualTo("CA");
        assertThat(r.has("reservationProfiles")).isFalse();
    }

    @Test
    void aPaidReservationGoesWithThePaymentsMethod() {
        var paid = List.of(new Payment("P-1", PaymentType.DEPOSIT, "VISA", new BigDecimal("150"), IN.minusDays(10), null));
        var r = payload.build(reservation(paid, "AD"), codes(), "RIUPMI", "G1", null, null, null)
                .path("reservations").path("reservation").get(0);

        assertThat(r.path("reservationPaymentMethods").get(0).path("paymentMethod").asText()).isEqualTo("VA");
    }
}
