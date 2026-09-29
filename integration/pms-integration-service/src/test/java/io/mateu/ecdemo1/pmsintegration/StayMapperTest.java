package io.mateu.ecdemo1.pmsintegration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.Person;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.PmsStatus;
import io.mateu.ecdemo1.pmsintegration.frontoffice.StayMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An Opera reservation into the front office's stay — in the shapes OHIP UAT answers for XMAR: one
 * the CRS wrote (its locator under the run's context), one born in Opera, a cancellation and a
 * no-show.
 */
class StayMapperTest {

    static final ObjectMapper JSON = new ObjectMapper();
    static final StayMapper.Context NO_CUSTOMER = new StayMapper.Context("ECDEMO1-09271718", Set.of("NOSHOW"), null, null);

    /** As OHIP answers GET …/reservations/{id}?fetchInstructions=Reservation&fetchInstructions=Packages. */
    static JsonNode reservation(String status, String cancellation, String references) throws Exception {
        return JSON.readTree("""
                {
                  "reservationIdList": [{"id": "39484601", "type": "Reservation"},
                                        {"id": "268334379", "type": "Confirmation"}],
                  "externalReferences": [%s],
                  "roomStay": {
                    "roomRates": [{
                      "total": {"amountBeforeTax": 186},
                      "rates": {"rate": [{"base": {"amountBeforeTax": 186, "currencyCode": "MUR"}}]},
                      "guestCounts": {"adults": 2, "children": 0},
                      "roomType": "STDK", "ratePlanCode": "406484DIRXM",
                      "sourceCode": "CRSN", "sourceCodeDescription": "Central Reservation"
                    }],
                    "guestCounts": {"adults": 2, "children": 1},
                    "arrivalDate": "2026-11-10", "departureDate": "2026-11-12",
                    "total": {"amountBeforeTax": 372}
                  },
                  "reservationGuests": [{
                    "profileInfo": {
                      "profileIdList": [{"id": "20546095", "type": "Profile"}],
                      "profile": {
                        "customer": {"personName": [{"givenName": "Nora", "surname": "Moreau", "nameType": "Primary"},
                                                    {"nameType": "External"}]},
                        "telephones": {"telephoneInfo": [
                          {"telephone": {"phoneTechType": "WEBPAGE", "phoneUseType": "WEB", "phoneNumber": "WRC3C25G-1"}},
                          {"telephone": {"phoneTechType": "PHONE", "phoneUseType": "MOBILE", "phoneNumber": "+32 450323958", "primaryInd": true}}]},
                        "emails": {"emailInfo": [{"email": {"emailAddress": "nora.moreau@example.com", "primaryInd": true}}]}
                      }
                    },
                    "primary": true
                  }],
                  "reservationPackages": [{"packageCode": "BRKFST", "startDate": "2026-11-10", "endDate": "2026-11-12"}],
                  "reservationStatus": "%s",
                  %s
                  "createDateTime": "2026-09-27 21:50:02.0",
                  "lastModifyDateTime": "2026-09-27 21:57:59.0"
                }
                """.formatted(references, status, cancellation));
    }

    static final String CRS_REFS = """
            {"id": "268334379", "idExtension": 1, "idContext": "OPERA"}, {"id": "3PJ492", "idContext": "ECDEMO1-09271718"}""";

    @Test
    void aReservationTheCrsWroteIsTheStayOfItsLocatorWithOperasCodes() throws Exception {
        var stay = StayMapper.toWriteStay("XMAR", reservation("Reserved", "", CRS_REFS), NO_CUSTOMER);
        assertThat(stay.pmsReservationId()).isEqualTo("39484601");
        assertThat(stay.confirmationNumber()).isEqualTo("268334379");
        assertThat(stay.crsLocator()).isEqualTo("3PJ492");
        assertThat(stay.externalReferences()).isEmpty();
        assertThat(stay.pmsVersion()).isEqualTo("2026-09-27T21:57:59");
        assertThat(stay.status()).isEqualTo(PmsStatus.RESERVED);
        assertThat(stay.roomTypeCode()).isEqualTo("STDK");
        assertThat(stay.ratePlanCode()).isEqualTo("406484DIRXM");
        assertThat(stay.boardCode()).isEqualTo("BRKFST");
        assertThat(stay.checkIn()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(stay.checkOut()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(stay.pax()).isEqualTo(3);
        assertThat(stay.total()).isEqualByComparingTo(new BigDecimal("372"));
        assertThat(stay.currency()).isEqualTo("MUR");
        assertThat(stay.agency()).isEqualTo("Directo · Central Reservation");
        assertThat(stay.key()).isEqualTo("XMAR/39484601");
    }

    @Test
    void theHolderIsOperasPrimaryGuestWithoutTheWebPageOperaKeepsAsAPhone() throws Exception {
        var holder = StayMapper.toWriteStay("XMAR", reservation("Reserved", "", CRS_REFS), NO_CUSTOMER).holder();
        assertThat(holder.name()).isEqualTo("Nora Moreau");
        assertThat(holder.pmsProfileId()).isEqualTo("20546095");
        assertThat(holder.email()).isEqualTo("nora.moreau@example.com");
        assertThat(holder.phone()).isEqualTo("+32 450323958");
        assertThat(holder.customerId()).isNull();
    }

    @Test
    void whenTheMdmKnowsTheProfileItsCustomerAndGoldenRecordGoOverOperas() throws Exception {
        var context = new StayMapper.Context("ECDEMO1-09271718", Set.of("NOSHOW"), "C-7",
                new Person("C-7", "20546095", "Nora Moreau Dupont", "X1234567", null, "+33 600000000"));
        var holder = StayMapper.toWriteStay("XMAR", reservation("Reserved", "", CRS_REFS), context).holder();
        assertThat(holder.customerId()).isEqualTo("C-7");
        assertThat(holder.name()).isEqualTo("Nora Moreau Dupont");
        assertThat(holder.document()).isEqualTo("X1234567");
        assertThat(holder.email()).isEqualTo("nora.moreau@example.com");
        assertThat(holder.phone()).isEqualTo("+33 600000000");
    }

    @Test
    void aReservationBornInOperaHasNoCrsLocatorAndKeepsItsReferences() throws Exception {
        var refs = """
                {"id": "263428862", "idContext": "OPERA"}, {"id": "WRC3C25G-1", "idContext": "CRS"}""";
        var stay = StayMapper.toWriteStay("XMAR", reservation("Reserved", "", refs), NO_CUSTOMER);
        assertThat(stay.crsLocator()).isNull();
        assertThat(stay.externalReferences()).containsExactly("WRC3C25G-1");
    }

    @Test
    void anOldRunsContextIsNotThisCrsLocator() throws Exception {
        var refs = """
                {"id": "3PJ492", "idContext": "ECDEMO1"}""";
        var stay = StayMapper.toWriteStay("XMAR", reservation("Reserved", "", refs), NO_CUSTOMER);
        assertThat(stay.crsLocator()).isNull();
        assertThat(stay.externalReferences()).containsExactly("3PJ492");
    }

    @Test
    void roomOnlyHasNoBoard() throws Exception {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) reservation("Reserved", "", CRS_REFS);
        node.remove("reservationPackages");
        assertThat(StayMapper.toWriteStay("XMAR", node, NO_CUSTOMER).boardCode()).isNull();
    }

    @Test
    void aCancellationIsCancelled() throws Exception {
        var stay = StayMapper.toWriteStay("XMAR", reservation("Cancelled", """
                "cancellation": {"description": "Cancelled in the CRS (reason OTR)", "code": "OTROS"},""", CRS_REFS), NO_CUSTOMER);
        assertThat(stay.status()).isEqualTo(PmsStatus.CANCELLED);
    }

    @Test
    void aCancellationWithANoShowCodeIsANoShowCostingWhatOperaStillCharges() throws Exception {
        var stay = StayMapper.toWriteStay("XMAR", reservation("Cancelled", """
                "cancellation": {"description": "No show: cancelled in the CRS with a fee of 139.50 EUR (of 558.00)", "code": "NOSHOW"},""",
                CRS_REFS), NO_CUSTOMER);
        assertThat(stay.status()).isEqualTo(PmsStatus.NO_SHOW);
        assertThat(stay.total()).isEqualByComparingTo(new BigDecimal("372"));
    }

    @Test
    void operasOwnNoShowIsANoShow() throws Exception {
        assertThat(StayMapper.toWriteStay("XMAR", reservation("NoShow", "", CRS_REFS), NO_CUSTOMER).status())
                .isEqualTo(PmsStatus.NO_SHOW);
    }

    @Test
    void guestsOperaHasInTheHouseOrCheckedOutAreSoForTheFrontOffice() throws Exception {
        assertThat(StayMapper.toWriteStay("XMAR", reservation("InHouse", "", CRS_REFS), NO_CUSTOMER).status())
                .isEqualTo(PmsStatus.IN_HOUSE);
        assertThat(StayMapper.toWriteStay("XMAR", reservation("CheckedOut", "", CRS_REFS), NO_CUSTOMER).status())
                .isEqualTo(PmsStatus.CHECKED_OUT);
    }

    @Test
    void aTravelAgentOnTheReservationIsWhoSoldIt() throws Exception {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) reservation("Reserved", "", CRS_REFS);
        node.set("reservationProfiles", JSON.readTree("""
                {"reservationProfile": [{"profile": {"company": {"companyName": "P.DIFERIDO RIU CLASS CA"}},
                                         "reservationProfileType": "TravelAgent"}]}"""));
        assertThat(StayMapper.toWriteStay("XMAR", node, NO_CUSTOMER).agency()).isEqualTo("P.DIFERIDO RIU CLASS CA");
    }

    @Test
    void operasTimesBecomeIsoLocalDateTimes() {
        assertThat(StayMapper.isoLocal("2026-03-13 22:01:45.0")).isEqualTo("2026-03-13T22:01:45");
        assertThat(StayMapper.isoLocal("2026-03-13")).isEqualTo("2026-03-13T00:00:00");
        assertThat(StayMapper.isoLocal(null)).isEmpty();
    }
}
