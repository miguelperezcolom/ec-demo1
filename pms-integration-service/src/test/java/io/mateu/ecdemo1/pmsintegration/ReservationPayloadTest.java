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
import io.mateu.ecdemo1.pmsintegration.ohip.PmsRejectedException;
import io.mateu.ecdemo1.pmsintegration.write.PackageRules;
import io.mateu.ecdemo1.pmsintegration.write.ReservationPayload;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What an integration reservation becomes on the wire to OHIP. */
class ReservationPayloadTest {

    static final OhipProperties PROPERTIES =
            new OhipProperties("RIUCRS", "CRS_VERSION", "CA", Duration.ofSeconds(5), null, null, null, null, null);

    final ReservationPayload payload = new ReservationPayload(new ObjectMapper(), PROPERTIES, PackageRules.NONE);

    /**
     * XMAR's packages as OHIP UAT answers them: BKF is not sold separately, BKFST is; the rate plan
     * EXP_BB carries BKFST.
     */
    static final PackageRules XMAR = new PackageRules() {
        @Override
        public boolean soldSeparately(String hotelId, String packageCode) {
            return !"BKF".equals(packageCode);
        }

        @Override
        public Set<String> includedIn(String hotelId, String ratePlanCode) {
            return "EXP_BB".equals(ratePlanCode) ? Set.of("BKFST") : Set.of();
        }
    };

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
        assertThat(rate.path("guestCounts").path("childAges").get(0).path("age").asInt()).isEqualTo(7);
        assertThat(rate.path("rates").path("rate")).hasSize(2);
        assertThat(rate.path("rates").path("rate").get(1).path("start").asText()).isEqualTo("2026-10-10");
        assertThat(rate.path("rates").path("rate").get(1).path("base").path("amountBeforeTax").decimalValue()).isEqualByComparingTo("133.50");
        assertThat(rate.path("total").path("amountBeforeTax").decimalValue()).isEqualByComparingTo("281.40");
        assertThat(r.path("reservationPackages").get(0).path("packageCode").asText()).isEqualTo("BKFST");
    }

    @Test
    void itCarriesTheLocatorTheVoucherTheVersionTheCustomReferenceAndTheProfiles() {
        var r = payload.build(reservation(List.of(), "AD"), codes(), "RIUPMI", "G1", partner(BillingMode.FRONT), "P1", "Agent")
                .path("reservations").path("reservation").get(0);

        assertThat(r.path("externalReferences").toString()).contains("\"id\":\"LOC1\",\"idContext\":\"RIUCRS\"")
                .contains("\"id\":\"NT-42\",\"idContext\":\"NORDTRAVEL\"");
        assertThat(r.path("customReference").asText()).isEqualTo("EC-DEMO1");
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

    static IntegrationClients.Resolved codes(String board, String ratePlan) {
        return new IntegrationClients.Resolved(List.of(
                new Translation(CodeType.CHANNEL, "TTOO", "TOUROP", Map.of("marketCode", "TOUR")),
                new Translation(CodeType.ROOM_TYPE, "DBL", "STDK", Map.of()),
                new Translation(CodeType.RATE_PLAN, "TTOO", ratePlan, Map.of()),
                new Translation(CodeType.BOARD, "AD", board, Map.of())), List.of());
    }

    @Test
    void childrensAgesGoAsObjectsWithAnAgeAsOhipTakesThem() throws Exception {
        var mapper = new ObjectMapper();
        var r = reservation(List.of(), "AD");
        var twoChildren = new Reservation(r.hotelCode(), r.locator(), r.version(), r.status(), r.channelCode(), r.partnerCode(),
                r.externalReference(), r.arrival(), r.departure(), r.currency(), r.holder(),
                List.of(new Room(1, "DBL", "TTOO", "AD", 1, List.of(2, 3), List.of(), r.rooms().getFirst().nightlyRates()),
                        new Room(2, "DBL", "TTOO", "AD", 2, List.of(), List.of(), r.rooms().getFirst().nightlyRates())),
                r.payments(), r.totalAmount(), r.comments(), null);
        var stay = payload.build(twoChildren, codes(), "RIUPMI", "G1", null, null, null)
                .path("reservations").path("reservation").get(0).path("roomStay");

        assertThat(stay.path("roomRates").get(0).path("guestCounts"))
                .isEqualTo(mapper.readTree("{\"adults\":1,\"children\":2,\"childAges\":[{\"age\":2},{\"age\":3}]}"));
        assertThat(stay.path("roomRates").get(1).path("guestCounts"))
                .isEqualTo(mapper.readTree("{\"adults\":2,\"children\":0}"));
        assertThat(stay.path("guestCounts")).isEqualTo(mapper.readTree("{\"adults\":3,\"children\":2}"));
    }

    @Test
    void aBoardTheRatePlanAlreadyCarriesIsNotAddedAgain() {
        var withRate = new ReservationPayload(new ObjectMapper(), PROPERTIES, XMAR);
        var r = withRate.build(reservation(List.of(), "AD"), codes("BKFST", "EXP_BB"), "XMAR", "G1", null, null, null)
                .path("reservations").path("reservation").get(0);

        assertThat(r.path("reservationPackages")).isEmpty();
    }

    @Test
    void aBoardSoldSeparatelyGoesAsAPackageWithARatePlanThatDoesNotCarryIt() {
        var withRate = new ReservationPayload(new ObjectMapper(), PROPERTIES, XMAR);
        var r = withRate.build(reservation(List.of(), "AD"), codes("BKFST", "406451TUFXM"), "XMAR", "G1", null, null, null)
                .path("reservations").path("reservation").get(0);

        assertThat(r.path("reservationPackages").get(0).path("packageCode").asText()).isEqualTo("BKFST");
    }

    @Test
    void aBoardThePropertyDoesNotSellSeparatelyAndTheRatePlanDoesNotCarryIsRefusedBeforeOperaSeesIt() {
        var withRate = new ReservationPayload(new ObjectMapper(), PROPERTIES, XMAR);

        assertThatThrownBy(() -> withRate.build(reservation(List.of(), "AD"), codes("BKF", "406451TUFXM"), "XMAR", "G1",
                null, null, null))
                .isInstanceOfSatisfying(PmsRejectedException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ReservationPayload.BOARD_NOT_SOLD_SEPARATELY))
                .hasMessageContaining("board AD is package BKF in XMAR, which XMAR does not sell separately")
                .hasMessageContaining("rate plan TTOO (406451TUFXM in Opera) does not include it");
    }
}
