package io.mateu.ecdemo1.loyalty;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.customer.CustomersMerged;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeEvent.StayClosed;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeEvent.StayGuest;
import io.mateu.ecdemo1.loyalty.application.Loyalty;
import io.mateu.ecdemo1.loyalty.infra.in.async.CustomerEventsConsumer;
import io.mateu.ecdemo1.loyalty.infra.in.async.FrontOfficeEventsConsumer;
import io.mateu.ecdemo1.loyalty.infra.in.ui.LoyaltyHome;
import io.mateu.ecdemo1.loyalty.infra.in.ui.pages.AccrualsPage;
import io.mateu.ecdemo1.loyalty.infra.in.ui.pages.MemberCrud;
import io.mateu.ecdemo1.loyalty.infra.in.ui.pages.MemberFilters;
import io.mateu.ecdemo1.loyalty.store.Tier;
import io.mateu.uidl.data.Pageable;
import io.mateu.uidl.data.SearchRequest;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Riu Class: members over REST, the points a closed stay earns (once), the tiers they reach, a merge
 * moving a membership, and the screens and MCP tools the service serves.
 */
class LoyaltyTest extends PostgresAndBroker {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    Loyalty loyalty;
    @Autowired
    FrontOfficeEventsConsumer frontOffice;
    @Autowired
    CustomerEventsConsumer customers;
    @Autowired
    ApplicationContext context;

    void putMember(String number, String body) throws Exception {
        mvc.perform(put("/members/" + number).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    static StayClosed stay(String eventId, String stayId, int nights, StayGuest... guests) {
        return new StayClosed(eventId, Instant.parse("2026-11-12T09:30:00Z"), "MRU01", stayId, stayId, "XMAR", "123456",
                LocalDate.of(2026, 11, 12).minusDays(nights), LocalDate.of(2026, 11, 12), nights, "205", "JS-SEA",
                "TODO-INCLUIDO", Arrays.asList(guests), List.of(), BigDecimal.ZERO, "MUR");
    }

    /** Through the consumer bean, as the binding hands it a record. */
    void deliver(Object event) throws Exception {
        var message = MessageBuilder.withPayload(json.writeValueAsBytes(event)).build();
        if (event instanceof StayClosed) {
            frontOffice.consumeFrontOfficeEvents().accept(message);
        } else {
            customers.consumeCustomerEvents().accept(message);
        }
    }

    long points(String number) {
        return loyalty.get(number).points;
    }

    @Test
    void aMemberIsPutAndFoundByNumberOrByCustomer() throws Exception {
        putMember(" rc12345678 ", """
                {"customerCode":"C-00042","tier":"GOLD","points":12500,"memberSince":"2019-03-01","somethingNew":1}""");

        mvc.perform(get("/members/RC12345678")).andExpect(status().isOk())
                .andExpect(jsonPath("$.memberNumber").value("RC12345678"))
                .andExpect(jsonPath("$.customerCode").value("C-00042"))
                .andExpect(jsonPath("$.tier").value("GOLD"))
                .andExpect(jsonPath("$.points").value(12500))
                .andExpect(jsonPath("$.memberSince").value("2019-03-01"))
                .andExpect(jsonPath("$.asOf").isNotEmpty());
        mvc.perform(get("/members/rc12345678")).andExpect(status().isOk());
        mvc.perform(get("/members").param("customerCode", "C-00042")).andExpect(status().isOk())
                .andExpect(jsonPath("$.memberNumber").value("RC12345678"));

        // A PUT that only changes the customer keeps the rest.
        putMember("RC12345678", """
                {"customerCode":"C-00043"}""");
        mvc.perform(get("/members").param("customerCode", "C-00043")).andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("GOLD"))
                .andExpect(jsonPath("$.points").value(12500))
                .andExpect(jsonPath("$.memberSince").value("2019-03-01"));

        // Without a tier, a new member gets what its points are worth, and joins today.
        putMember("RC2", """
                {"customerCode":"C-2","points":45000}""");
        mvc.perform(get("/members/RC2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("PLATINUM"))
                .andExpect(jsonPath("$.memberSince").value(LocalDate.now().toString()));
    }

    @Test
    void unknownMembersAre404AndNonsenseIs400() throws Exception {
        mvc.perform(get("/members/RC00000000")).andExpect(status().isNotFound());
        mvc.perform(get("/members").param("customerCode", "C-NOBODY")).andExpect(status().isNotFound());
        mvc.perform(put("/members/RC1").contentType(MediaType.APPLICATION_JSON).content("{\"tier\":\"GOLD\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/members/RC1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerCode\":\"C-1\",\"tier\":\"DIAMOND\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/members/RC1")).andExpect(status().isNotFound());
    }

    @Test
    void aClosedStayEarnsTheHolderAndCompanionsTheirPointsOnce() throws Exception {
        loyalty.upsert("RC1", new Loyalty.MemberUpdate("C-1", null, 0L, null));
        loyalty.upsert("RC2", new Loyalty.MemberUpdate("C-2", null, 1000L, null));

        var closed = stay("E-1", "S-1", 4, new StayGuest("C-1", true), new StayGuest("C-2", false),
                new StayGuest("pax-3", false));
        deliver(closed);

        assertThat(points("RC1")).isEqualTo(400);
        assertThat(points("RC2")).isEqualTo(1200);
        assertThat(loyalty.accrualsOf("RC1")).singleElement().satisfies(a -> {
            assertThat(a.stayId).isEqualTo("S-1");
            assertThat(a.hotelCode).isEqualTo("MRU01");
            assertThat(a.nights).isEqualTo(4);
            assertThat(a.points).isEqualTo(400);
        });

        // Redelivered, and the same stay closed again under another event id: nothing more.
        deliver(closed);
        deliver(stay("E-1b", "S-1", 4, new StayGuest("C-1", true), new StayGuest("C-2", false)));
        assertThat(points("RC1")).isEqualTo(400);
        assertThat(points("RC2")).isEqualTo(1200);
        assertThat(loyalty.accrualsOf("RC2")).hasSize(1);
    }

    @Test
    void theTierGoesUpWithThePointsAndNeverDown() throws Exception {
        loyalty.upsert("RC1", new Loyalty.MemberUpdate("C-1", null, 9_800L, null));
        assertThat(loyalty.get("RC1").tier()).isEqualTo(Tier.SILVER);

        deliver(stay("E-1", "S-1", 2, new StayGuest("C-1", true)));   // 10 000
        assertThat(loyalty.get("RC1").tier()).isEqualTo(Tier.GOLD);

        loyalty.upsert("RC1", new Loyalty.MemberUpdate("C-1", null, 39_950L, null));
        deliver(stay("E-2", "S-2", 1, new StayGuest("C-1", false)));  // 40 000
        assertThat(loyalty.get("RC1").tier()).isEqualTo(Tier.PLATINUM);

        // A tier granted above the points is kept when a stay earns more.
        loyalty.upsert("RC2", new Loyalty.MemberUpdate("C-2", Tier.PLATINUM, 0L, null));
        deliver(stay("E-3", "S-3", 1, new StayGuest("C-2", true)));
        assertThat(loyalty.get("RC2").tier()).isEqualTo(Tier.PLATINUM);
        assertThat(points("RC2")).isEqualTo(100);
    }

    @Test
    void aMergeMovesTheMembershipToTheSurvivor() throws Exception {
        loyalty.upsert("RC1", new Loyalty.MemberUpdate("C-ABSORBED", null, 500L, null));

        deliver(new CustomersMerged("M-1", Instant.now(), "C-SURVIVOR", 3, null, "C-ABSORBED", List.of()));

        assertThat(loyalty.get("RC1").customerCode).isEqualTo("C-SURVIVOR");
        mvc.perform(get("/members").param("customerCode", "C-SURVIVOR")).andExpect(status().isOk())
                .andExpect(jsonPath("$.memberNumber").value("RC1"));
        mvc.perform(get("/members").param("customerCode", "C-ABSORBED")).andExpect(status().isNotFound());
        // The stays the survivor closes from now on earn it the points.
        deliver(stay("E-1", "S-1", 1, new StayGuest("C-SURVIVOR", true)));
        assertThat(points("RC1")).isEqualTo(600);
    }

    @Test
    void aStayClosedOnTheTopicEarnsItsPoints() throws Exception {
        loyalty.upsert("RC9", new Loyalty.MemberUpdate("C-9", null, 0L, null));
        var config = Map.<String, Object>of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, redpanda.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (var producer = new KafkaProducer<String, String>(config)) {
            producer.send(new ProducerRecord<>("front-office-events", "S-9",
                    json.writeValueAsString(stay("E-9-topic", "S-9", 3, new StayGuest("C-9", true))))).get();
        }

        var deadline = System.currentTimeMillis() + 60_000;
        while (points("RC9") == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(250);
        }
        assertThat(points("RC9")).isEqualTo(300);
    }

    @Test
    void theMembersScreenSearchesByNumberOrCustomerAndFiltersByTier() {
        loyalty.upsert("RC1", new Loyalty.MemberUpdate("C-ANA", Tier.GOLD, 12_000L, null));
        loyalty.upsert("RC2", new Loyalty.MemberUpdate("C-LUIS", null, 100L, null));

        assertThat(found("", null)).containsExactlyInAnyOrder("RC1", "RC2");
        assertThat(found("rc1", null)).containsExactly("RC1");
        assertThat(found("luis", null)).containsExactly("RC2");
        var gold = new MemberFilters();
        gold.tier = java.util.Set.of(Tier.GOLD);
        assertThat(found("", gold)).containsExactly("RC1");
    }

    List<String> found(String text, MemberFilters filters) {
        return context.getBean(MemberCrud.class).search(new SearchRequest(text, filters, null,
                new Pageable(0, 20, List.of())), null).page().content().stream().map(r -> r.memberNumber()).toList();
    }

    /** What a shell gets for a route of Riu Class, as its renderer asks for it. */
    String wire(String route, String consumedRoute, String serverSideType) throws Exception {
        var body = json.writeValueAsString(Map.of("route", route, "consumedRoute", consumedRoute, "actionId", "",
                "componentState", Map.of(), "appState", Map.of(), "serverSideType", serverSideType));
        var started = mvc.perform(post("/_loyalty/mateu/v3/sync" + route).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
        return mvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void theScreensRenderOverTheWire() throws Exception {
        loyalty.upsert("RC1", new Loyalty.MemberUpdate("C-ANA", Tier.GOLD, 12_000L, LocalDate.of(2019, 3, 1)));
        deliver(stay("E-1", "S-77", 2, new StayGuest("C-ANA", true)));

        for (var route : Map.of("/loyalty/members", MemberCrud.class, "/loyalty/accruals", AccrualsPage.class).entrySet()) {
            assertThat(wire(route.getKey(), "", LoyaltyHome.class.getName()))
                    .contains("\"serverSideType\":\"" + route.getValue().getName() + "\"")
                    .doesNotContain("Not found.").doesNotContain("\"variant\":\"error\"");
        }
        // The accruals listing loads on open: it is not navigable, and would otherwise wait for a search.
        assertThat(wire("/loyalty/accruals", "", LoyaltyHome.class.getName())).contains("\"type\":\"OnLoad\"");
        var member = wire("/loyalty/members/RC1", "/loyalty/members", MemberCrud.class.getName());
        assertThat(member).doesNotContain("\"variant\":\"error\"").contains("C-ANA").contains("S-77")
                .contains("Acumulaciones");
    }

    @Test
    void theMcpToolsAreOffered() {
        var names = Arrays.stream(context.getBean("loyaltyToolCallbackProvider", ToolCallbackProvider.class)
                .getToolCallbacks()).map(t -> t.getToolDefinition().name()).toList();
        assertThat(names).containsExactlyInAnyOrder("getMember", "findMemberByCustomer", "listMembers", "getAccruals");
    }
}
