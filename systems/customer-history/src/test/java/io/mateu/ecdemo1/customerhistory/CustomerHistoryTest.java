package io.mateu.ecdemo1.customerhistory;

import io.mateu.ecdemo1.customerhistory.application.CustomerHistory;
import io.mateu.ecdemo1.integration.model.customer.CustomersMerged;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeEvent.ChargeKind;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeEvent.ChargeTotal;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeEvent.StayClosed;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeEvent.StayGuest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The history against a real Postgres: what it takes, how it adds up, merges and the demo's seed. */
@SpringBootTest
@AutoConfigureMockMvc
class CustomerHistoryTest extends Containers {

    @Autowired
    CustomerHistory history;
    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void empty() {
        jdbc.update("truncate customer_stay, customer_alias, inbox_entry");
    }

    static StayClosed closed(String eventId, String hotel, String stayId, LocalDate arrival, int nights,
                             List<StayGuest> guests, String total, String currency) {
        var charges = List.of(new ChargeTotal(ChargeKind.ADD_ON, new BigDecimal("20.00")),
                new ChargeTotal(ChargeKind.CONSUMPTION, new BigDecimal(total).subtract(new BigDecimal("20.00"))));
        return new StayClosed(eventId, Instant.parse("2026-08-20T10:00:00Z"), hotel, stayId, "L-" + stayId, "XMAR",
                "123", arrival, arrival.plusDays(nights), nights, "205", "JS-SEA", "TODO-INCLUIDO", guests, charges,
                new BigDecimal(total), currency);
    }

    static String id() {
        return UUID.randomUUID().toString();
    }

    int rows(String where) {
        return jdbc.queryForObject("select count(*) from customer_stay where " + where, Integer.class);
    }

    @Test
    void aClosedStayIsOneRowPerCustomerOfTheChainAndTakingItAgainChangesNothing() {
        var event = closed("E-1", "MRU01", "S-1", LocalDate.of(2026, 8, 14), 6,
                List.of(new StayGuest("C-00042", true), new StayGuest("pax-2", false),
                        new StayGuest("C-00077", false), new StayGuest("opera-991", false), new StayGuest("wi-3", false)),
                "120.50", "MUR");

        assertThat(history.take(event)).isEqualTo(2);
        assertThat(history.take(event)).isZero();   // the inbox: the same event, once
        // a redelivery under another event id still writes the same two rows
        assertThat(history.take(closed("E-1b", "MRU01", "S-1", LocalDate.of(2026, 8, 14), 6,
                List.of(new StayGuest("C-00042", true), new StayGuest("C-00077", false)), "120.50", "MUR"))).isEqualTo(2);

        assertThat(rows("true")).isEqualTo(2);
        assertThat(rows("customer_id like 'C-%'")).isEqualTo(2);
        assertThat(rows("customer_id = 'C-00042' and holder and add_on_total = 20 and consumption_total = 100.50 "
                + "and late_check_out_total = 0 and total = 120.50 and source = 'FRONT_OFFICE'")).isEqualTo(1);
        assertThat(history.stays("C-00077", 0, 20).items()).singleElement()
                .satisfies(s -> assertThat(s.holder()).isFalse());
    }

    @Test
    void theSummaryAddsUpTheStays() throws Exception {
        history.take(closed(id(), "PMI01", "S-1", LocalDate.of(2023, 5, 2), 5, List.of(new StayGuest("C-1", true)), "100.00", "MUR"));
        history.take(closed(id(), "PMI01", "S-2", LocalDate.of(2025, 7, 1), 3, List.of(new StayGuest("C-1", true)), "200.00", "MUR"));
        history.take(closed(id(), "CUN01", "S-3", LocalDate.of(2026, 8, 14), 6, List.of(new StayGuest("C-1", true)), "120.50", "MUR"));
        history.take(closed(id(), "CUN01", "S-4", LocalDate.of(2024, 1, 10), 2, List.of(new StayGuest("C-1", true)), "999.00", "EUR"));

        mvc.perform(get("/customers/C-1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("C-1"))
                .andExpect(jsonPath("$.stays").value(4))
                .andExpect(jsonPath("$.nights").value(16))
                .andExpect(jsonPath("$.firstStay").value("2023-05-02"))
                .andExpect(jsonPath("$.lastStay").value("2026-08-20"))
                .andExpect(jsonPath("$.lastStays.length()").value(3))
                .andExpect(jsonPath("$.lastStays[0].hotelCode").value("CUN01"))
                .andExpect(jsonPath("$.lastStays[0].arrival").value("2026-08-14"))
                .andExpect(jsonPath("$.lastStays[0].departure").value("2026-08-20"))
                .andExpect(jsonPath("$.lastStays[0].roomNumber").value("205"))
                .andExpect(jsonPath("$.lastStays[0].roomType").value("JS-SEA"))
                .andExpect(jsonPath("$.lastStays[1].hotelCode").value("PMI01"))
                .andExpect(jsonPath("$.hotels").value(2))
                // two each: the tie goes to the hotel of the most recent stay
                .andExpect(jsonPath("$.topHotel").value("CUN01"))
                // three in MUR, one in EUR: the MUR ones, not converted
                .andExpect(jsonPath("$.spend.amount").value(420.50))
                .andExpect(jsonPath("$.spend.currency").value("MUR"));

        mvc.perform(get("/customers/C-1/stays?page=1&size=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("C-1"))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].stayId").value("S-1"))
                .andExpect(jsonPath("$.items[0].addOnTotal").value(20.00))
                .andExpect(jsonPath("$.items[0].consumptionTotal").value(80.00))
                .andExpect(jsonPath("$.items[0].source").value("FRONT_OFFICE"));
    }

    @Test
    void anUnknownCustomerHasAHistoryOfZeros() throws Exception {
        mvc.perform(get("/customers/C-99999/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("C-99999"))
                .andExpect(jsonPath("$.stays").value(0))
                .andExpect(jsonPath("$.nights").value(0))
                .andExpect(jsonPath("$.hotels").value(0))
                .andExpect(jsonPath("$.lastStays.length()").value(0))
                .andExpect(jsonPath("$.spend.amount").value(0.0));
        mvc.perform(get("/customers/C-99999/stays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void anAbsorbedCodesStaysAreTheSurvivorsAndAskingByItAnswersTheSurvivor() throws Exception {
        history.take(closed(id(), "PMI01", "S-1", LocalDate.of(2024, 5, 2), 5, List.of(new StayGuest("C-00042", true)), "10.00", "MUR"));
        history.take(closed(id(), "MRU01", "S-2", LocalDate.of(2025, 5, 2), 4, List.of(new StayGuest("C-00051", true)), "30.00", "MUR"));
        history.take(closed(id(), "CUN01", "S-3", LocalDate.of(2023, 5, 2), 2, List.of(new StayGuest("C-00060", true)), "5.00", "MUR"));

        var merge = new CustomersMerged(id(), Instant.now(), "C-00042", 4, null, "C-00051", List.of());
        history.take(merge);
        history.take(merge);
        // a merge of a merge: C-00060 into C-00051, which is C-00042 now
        history.take(new CustomersMerged(id(), Instant.now(), "C-00051", 5, null, "C-00060", List.of()));

        for (var code : List.of("C-00042", "C-00051", "c-00060")) {
            mvc.perform(get("/customers/" + code + "/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerId").value("C-00042"))
                    .andExpect(jsonPath("$.stays").value(3))
                    .andExpect(jsonPath("$.nights").value(11))
                    .andExpect(jsonPath("$.spend.amount").value(45.00));
        }
        // the rows keep the code they were closed with
        assertThat(rows("customer_id = 'C-00051'")).isEqualTo(1);
        assertThat(history.stays("C-00051", 0, 20).customerId()).isEqualTo("C-00042");
    }

    @Test
    void theDemosPastStaysAreTheSameEveryTimeAndGoAwayAlone() throws Exception {
        history.take(closed(id(), "MRU01", "S-REAL", LocalDate.of(2026, 9, 1), 4, List.of(new StayGuest("C-00042", true)), "60.00", "MUR"));

        var body = "{\"customerId\":\"C-00042\",\"count\":5}";
        var first = mvc.perform(post("/demo/stays").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("C-00042"))
                .andExpect(jsonPath("$.stays").value(6))
                .andReturn().getResponse().getContentAsString();
        var stays = jdbc.queryForList("select id, hotel_code, arrival, total from customer_stay where source = 'DEMO' order by id");
        var second = mvc.perform(post("/demo/stays").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForList("select id, hotel_code, arrival, total from customer_stay where source = 'DEMO' order by id"))
                .isEqualTo(stays).hasSize(5);
        assertThat(stays).allSatisfy(s -> assertThat((String) s.get("id")).startsWith("HIST-C-00042-"));
        assertThat(rows("source = 'DEMO' and arrival < current_date - 365 and arrival > current_date - 4 * 366 "
                + "and nights between 3 and 10")).isEqualTo(5);

        // no count: four
        mvc.perform(post("/demo/stays").contentType(MediaType.APPLICATION_JSON).content("{\"customerId\":\"C-00042\"}"))
                .andExpect(jsonPath("$.stays").value(5));
        mvc.perform(post("/demo/stays").contentType(MediaType.APPLICATION_JSON).content("{\"count\":3}"))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(delete("/demo/stays/C-00042"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(4));
        assertThat(rows("true")).isEqualTo(1);
    }
}
