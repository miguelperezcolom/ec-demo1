package io.mateu.ecdemo1.operamock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "opera-mock.capacity-per-room-type=1")
@AutoConfigureMockMvc
class OperaMockTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper objectMapper;

    String token;

    @BeforeEach
    void authenticate() throws Exception {
        mvc.perform(delete("/_mock"));
        var basic = Base64.getEncoder().encodeToString("mock-client:mock-secret".getBytes(StandardCharsets.UTF_8));
        var body = mvc.perform(post("/oauth/v1/tokens").header("x-app-key", "mock-app-key").header("enterpriseId", "RIUE")
                        .header("Authorization", "Basic " + basic)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).content("grant_type=client_credentials"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(body).get("access_token").asText();
    }

    MockHttpServletRequestBuilder ohip(MockHttpServletRequestBuilder request, String hotel) {
        return request.header("x-app-key", "mock-app-key").header("Authorization", "Bearer " + token)
                .header("x-hotelid", hotel).contentType(MediaType.APPLICATION_JSON);
    }

    String guest() throws Exception {
        var location = mvc.perform(ohip(post("/crm/v1/profiles"), "RIUPMI").content("""
                        {"profileDetails":{"profileType":"Guest","customer":{"personName":[{"givenName":"Ana","surname":"García"}]}},
                         "externalReferences":[{"id":"HOLDER-L1","idContext":"RIUCRS"}]}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    static String reservation(String guest, String roomType, String locator) {
        return """
                {"reservations":{"reservation":[{"roomStay":{"arrivalDate":"2026-10-05","departureDate":"2026-10-07",
                  "roomRates":[{"roomType":"%s","ratePlanCode":"RACK","marketCode":"LEIS","sourceCode":"WEBDIR",
                                "start":"2026-10-05","end":"2026-10-07","fixedRate":true}]},
                  "reservationGuests":[{"profileInfo":{"profileIdList":[{"id":"%s","type":"Profile"}]},"primary":true}],
                  "externalReferences":[{"id":"%s","idContext":"RIUCRS"}],
                  "userDefinedFields":{"numericUDFs":[{"name":"CRS_VERSION","value":3}]}}]}}"""
                .formatted(roomType, guest, locator);
    }

    @Test
    void theEnterpriseListsTheChainsPropertiesAndSaysWhichAreConfigured() throws Exception {
        mvc.perform(get("/ent/config/v1/hotels").header("x-app-key", "mock-app-key")
                .header("Authorization", "Bearer " + token).header("x-hubid", "NOPE"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/ent/config/v1/hotels").header("x-app-key", "mock-app-key")
                        .header("Authorization", "Bearer " + token).header("x-hubid", "RIUE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotels[0].hotelId").value("RIUCUN"))
                .andExpect(jsonPath("$.hotels[0].configured").value(true))
                .andExpect(jsonPath("$.hotels[1].hotelId").value("RIUNEW"))
                .andExpect(jsonPath("$.hotels[1].configured").value(false))
                .andExpect(jsonPath("$.hotels[2].hotelId").value("RIUPMI"));
    }

    @Test
    void everyCallNeedsAValidTokenAndAHotelThisClientMaySee() throws Exception {
        mvc.perform(get("/rm/config/v1/hotels/RIUPMI/roomTypes").header("x-app-key", "mock-app-key")
                .header("Authorization", "Bearer nope").header("x-hotelid", "RIUPMI")).andExpect(status().isUnauthorized());
        mvc.perform(ohip(get("/rm/config/v1/hotels/RIUPMI/roomTypes"), "RIUE"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$['o:errorCode']").value("OPERAWS-GEN01244"));
        mvc.perform(ohip(get("/rm/config/v1/hotels/RIUPMI/roomTypes"), "RIUPMI"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roomTypesSummary[0].roomTypeSummary[0].roomType").value("SGLS"));
    }

    @Test
    void aReservationIsValidatedAgainstThePropertyAndFoundByItsExternalReference() throws Exception {
        var guest = guest();
        mvc.perform(ohip(post("/rsv/v1/hotels/RIUPMI/reservations"), "RIUPMI").content(reservation(guest, "DBL", "L1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Invalid room type DBL"));
        mvc.perform(ohip(post("/rsv/v1/hotels/RIUPMI/reservations"), "RIUPMI").content(reservation(guest, "STDK", "L1")))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"));

        mvc.perform(ohip(get("/rsv/v1/hotels/RIUPMI/reservations?externalReferenceIds=L1&externalSystemCodes=RIUCRS"), "RIUPMI"))
                .andExpect(jsonPath("$.reservations.totalResults").value(1))
                // A search answers summaries, as a real tenant: no UDFs — those take a get by id.
                .andExpect(jsonPath("$.reservations.reservationInfo[0].reservationGuest.id").value(guest))
                .andExpect(jsonPath("$.reservations.reservationInfo[0].userDefinedFields").doesNotExist());
        mvc.perform(ohip(get("/crm/v1/externalSystems/RIUCRS/profiles/HOLDER-L1"), "RIUPMI")).andExpect(status().isOk());
    }

    @Test
    void thePropertyRefusesWhenThereIsNoRoomLeft() throws Exception {
        var guest = guest();
        mvc.perform(ohip(post("/rsv/v1/hotels/RIUPMI/reservations"), "RIUPMI").content(reservation(guest, "JRST", "A")))
                .andExpect(status().isCreated());
        mvc.perform(ohip(post("/rsv/v1/hotels/RIUPMI/reservations"), "RIUPMI").content(reservation(guest, "JRST", "B")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$['o:errorCode']").value("MOCK-NOAVAIL"));
    }

    @Test
    void aCancelledReservationCannotBeUpdated() throws Exception {
        var guest = guest();
        var location = mvc.perform(ohip(post("/rsv/v1/hotels/RIUPMI/reservations"), "RIUPMI").content(reservation(guest, "STDT", "C")))
                .andReturn().getResponse().getHeader("Location");
        mvc.perform(ohip(post(location + "/cancellations"), "RIUPMI").content("""
                {"reason":{"code":"CUSTREQ"}}""")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$['o:errorCode']").value("OPERAWS-RSV11046"));
        mvc.perform(ohip(post(location + "/cancellations"), "RIUPMI").content("""
                {"reason":{"code":"CUSTREQ","description":"Customer request"}}""")).andExpect(status().isOk());
        mvc.perform(ohip(put(location), "RIUPMI").content(reservation(guest, "STDT", "C")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$['o:errorCode']").value("MOCK-CANCELLED"));
    }

    @Test
    void injectedFaultsAnswerTheNextCalls() throws Exception {
        mvc.perform(post("/_mock/faults?status=503&pathContains=/roomTypes&count=2"));
        mvc.perform(ohip(get("/rm/config/v1/hotels/RIUPMI/roomTypes"), "RIUPMI")).andExpect(status().isServiceUnavailable());
        mvc.perform(ohip(get("/rm/config/v1/hotels/RIUPMI/roomTypes"), "RIUPMI")).andExpect(status().isServiceUnavailable());
        mvc.perform(ohip(get("/rm/config/v1/hotels/RIUPMI/roomTypes"), "RIUPMI")).andExpect(status().isOk());
    }
}
