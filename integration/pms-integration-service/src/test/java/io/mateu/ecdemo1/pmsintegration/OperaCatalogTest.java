package io.mateu.ecdemo1.pmsintegration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.integration.model.integration.IntegrationView;
import io.mateu.ecdemo1.integration.model.integration.OhipConnection;
import io.mateu.ecdemo1.integration.model.mapping.CodeEntry;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.pmsintegration.config.OhipProperties;
import io.mateu.ecdemo1.pmsintegration.config.TolerantReader;
import io.mateu.ecdemo1.pmsintegration.connections.Connections;
import io.mateu.ecdemo1.pmsintegration.ohip.OhipClient;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaCatalog;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaPackages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * A property's catalog as Opera's answers become code entries — in the shapes a real tenant answers
 * (OHIP UAT, property XMAR), where the rate plans' names come only from the rate plan search.
 */
class OperaCatalogTest {

    HttpServer ohip;
    final List<String> calls = new ArrayList<>();
    OperaCatalog catalog;
    OhipClient client;

    @BeforeEach
    void start() throws Exception {
        ohip = HttpServer.create(new InetSocketAddress(0), 0);
        ohip.createContext("/oauth/v1/tokens", exchange -> reply(exchange,
                "{\"access_token\":\"t\",\"token_type\":\"Bearer\",\"expires_in\":3600}"));
        ohip.createContext("/", exchange -> {
            var uri = exchange.getRequestURI().toString();
            calls.add(uri);
            reply(exchange, answer(uri));
        });
        ohip.start();
        var gateway = "http://localhost:" + ohip.getAddress().getPort();
        var connection = new OhipConnection("XMAR", gateway, "app", "id", "secret", "RIUE");
        client = new OhipClient(new Connections() {
            @Override
            public Optional<OhipConnection> of(String pmsHotelCode) {
                return Optional.of(connection);
            }

            @Override
            public List<IntegrationView> integrations() {
                return List.of();
            }
        }, new OhipProperties(null, null, null, Duration.ofSeconds(2), null, null, null, null, null),
                new TolerantReader(new ObjectMapper()), Clock.systemUTC());
        catalog = new OperaCatalog(client, new Connections() {
            @Override
            public Optional<OhipConnection> of(String pmsHotelCode) {
                return Optional.of(connection);
            }

            @Override
            public List<IntegrationView> integrations() {
                return List.of();
            }
        });
    }

    @AfterEach
    void stop() {
        ohip.stop(0);
    }

    static String answer(String uri) {
        if (uri.startsWith("/rtp/v1/ratePlans?")) {
            // Two pages, the second asked at the offset the first answered.
            return uri.contains("offset=0") ? """
                    {"ratePlanShortInfoList":{"ratePlanShortInfo":[
                      {"primaryDetails":{"description":{"defaultText":"DIRECTOS XMU A26"}},"classifications":{"rateCategory":"GEN  "},"hotelId":"XMAR","ratePlanCode":"406484DIRXM"},
                      {"primaryDetails":{"description":{"defaultText":"OTAS A26 XMU RO"}},"classifications":{"rateCategory":"RO   "},"hotelId":"XMAR","ratePlanCode":"40379944"}],
                     "hasMore":true,"totalResults":3,"offset":2,"limit":2,"totalPages":2},"masterInfoList":[]}
                    """ : """
                    {"ratePlanShortInfoList":{"ratePlanShortInfo":[
                      {"primaryDetails":{"description":{"defaultText":"TUI UK A26 XMU"}},"hotelId":"XMAR","ratePlanCode":"399243TUUKX"}],
                     "hasMore":false,"totalResults":3,"offset":3,"limit":2,"totalPages":2},"masterInfoList":[]}
                    """;
        }
        if (uri.startsWith("/rtp/v1/hotels/XMAR/ratePlans/EXP_BB?fetchInstructions=Packages")) {
            return """
                    {"ratePlans":[{"hotelId":"XMAR","ratePlanCode":"EXP_BB","ratePackages":{
                      "packages":[{"code":"BRKFST","description":"BRKFST","quantity":1}],
                      "packageGroups":[{"code":"PENSION","packages":[{"code":"BEV"},{"code":"FOOD"}]}]}}]}
                    """;
        }
        if (uri.startsWith("/rtp/v1/hotels/")) {
            // The hotel's own list: codes, no description — what the connector no longer relies on.
            return "{\"ratePlans\":[{\"hotelId\":\"XMAR\",\"ratePlanCode\":\"406484DIRXM\"}]}";
        }
        if (uri.startsWith("/rm/config/v1/hotels/")) {
            return """
                    {"roomTypesSummary":[{"roomTypeSummary":[
                      {"roomType":"SUPE","shortDescription":{"defaultText":"Suite Swim Up"},"pseudo":false,"inactive":false},
                      {"roomType":"PM","shortDescription":{"defaultText":"Posting master"},"pseudo":true,"inactive":false}]}]}
                    """;
        }
        if (uri.startsWith("/rtp/v1/packages")) {
            return """
                    {"packageCodesList":{"packageCodes":[{"packageCodeShortInfo":[
                      {"code":"PENSTI","primaryDetails":{"description":"Pensión Todo Incluido"}},
                      {"code":"BKF","primaryDetails":{"description":"Pensión Desayuno Adulto"},"postingAttributes":{"sellSeparate":false}},
                      {"code":"BRKFST","primaryDetails":{"description":"BRKFST"},"postingAttributes":{"sellSeparate":true}}]}]}}
                    """;
        }
        if (uri.startsWith("/lov/v1/listOfValues/hotels/XMAR/paymentMethods")) {
            return "{\"listOfValues\":{\"items\":[{\"code\":\"MC\",\"name\":\"Master Card\",\"description\":\"Master Card\"}]}}";
        }
        return "{}";
    }

    static void reply(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void ratePlansComeWithTheirNamesFromTheRatePlanSearchPageByPage() {
        var plans = catalog.catalog("XMAR").stream().filter(e -> e.type() == CodeType.RATE_PLAN).toList();

        assertThat(plans).extracting(CodeEntry::hotelCode, CodeEntry::code, CodeEntry::description).containsExactly(
                tuple("XMAR", "406484DIRXM", "DIRECTOS XMU A26"),
                tuple("XMAR", "40379944", "OTAS A26 XMU RO"),
                tuple("XMAR", "399243TUUKX", "TUI UK A26 XMU"));
        assertThat(calls).filteredOn(c -> c.startsWith("/rtp/v1/ratePlans"))
                .containsExactly("/rtp/v1/ratePlans?hotelId=XMAR&limit=200&offset=0",
                        "/rtp/v1/ratePlans?hotelId=XMAR&limit=200&offset=2");
    }

    @Test
    void theOtherTypesKeepTheirDescriptionsAndPseudoRoomsStayOut() {
        var entries = catalog.catalog("XMAR");

        assertThat(entries).extracting(CodeEntry::type, CodeEntry::code, CodeEntry::description).contains(
                tuple(CodeType.ROOM_TYPE, "SUPE", "Suite Swim Up"),
                tuple(CodeType.BOARD, "NONE", "No package: room only"),
                tuple(CodeType.BOARD, "PENSTI", "Pensión Todo Incluido"),
                tuple(CodeType.PAYMENT_METHOD, "MC", "Master Card"));
        assertThat(entries).extracting(CodeEntry::code).doesNotContain("PM");
    }

    @Test
    void aPackageThePropertyDoesNotSellSeparatelyIsNoBoard() {
        var boards = catalog.catalog("XMAR").stream().filter(e -> e.type() == CodeType.BOARD).map(CodeEntry::code).toList();

        assertThat(boards).containsExactly("NONE", "PENSTI", "BRKFST");
    }

    @Test
    void theRulesOfPackagesAreReadFromTheListAndFromTheRatePlanWithItsGroups() {
        var packages = new OperaPackages(client, Clock.systemUTC());

        assertThat(packages.soldSeparately("XMAR", "BKF")).isFalse();
        assertThat(packages.soldSeparately("XMAR", "BRKFST")).isTrue();
        assertThat(packages.soldSeparately("XMAR", "PENSTI")).isTrue();
        assertThat(packages.includedIn("XMAR", "EXP_BB")).containsExactlyInAnyOrder("BRKFST", "BEV", "FOOD");
        packages.soldSeparately("XMAR", "FOOD");
        assertThat(calls).filteredOn(c -> c.startsWith("/rtp/v1/packages")).hasSize(1);
    }
}
