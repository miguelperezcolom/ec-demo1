package io.mateu.ecdemo1.pmsintegration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.integration.model.integration.IntegrationView;
import io.mateu.ecdemo1.integration.model.integration.OhipConnection;
import io.mateu.ecdemo1.integration.model.partner.BillingMode;
import io.mateu.ecdemo1.integration.model.partner.Partner;
import io.mateu.ecdemo1.integration.model.partner.PartnerType;
import io.mateu.ecdemo1.pmsintegration.config.OhipProperties;
import io.mateu.ecdemo1.pmsintegration.config.TolerantReader;
import io.mateu.ecdemo1.pmsintegration.connections.Connections;
import io.mateu.ecdemo1.pmsintegration.ohip.OhipClient;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaProfiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A partner exported to a tenant whose profiles take no external references, as the chain's: found by
 * its CorporateId — its code in the chain — and created with it, so a later search finds it.
 */
class PartnerProfilesTest {

    HttpServer ohip;
    final List<String> calls = new CopyOnWriteArrayList<>();
    volatile String searchAnswer = "{\"profileSummaries\":{\"totalResults\":0}}";
    OperaProfiles profiles;

    @BeforeEach
    void start() throws Exception {
        ohip = HttpServer.create(new InetSocketAddress(0), 0);
        ohip.createContext("/oauth/v1/tokens", exchange -> reply(exchange, 200,
                "{\"access_token\":\"t\",\"token_type\":\"Bearer\",\"expires_in\":3600}", null));
        ohip.createContext("/", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            calls.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " " + body);
            if ("POST".equals(exchange.getRequestMethod())) {
                reply(exchange, 201, "", "/crm/v1/profiles/20600001");
            } else {
                reply(exchange, 200, searchAnswer, null);
            }
        });
        ohip.start();
        var connection = new OhipConnection("XMAR", "http://localhost:" + ohip.getAddress().getPort(), "app", "id", "s", "RIUE");
        var properties = new OhipProperties("CRS", "UDFN01", "CASH", Duration.ofSeconds(2), "", false, false, null);
        var client = new OhipClient(new Connections() {
            @Override
            public Optional<OhipConnection> of(String pmsHotelCode) {
                return Optional.of(connection);
            }

            @Override
            public List<IntegrationView> integrations() {
                return List.of();
            }
        }, properties, new TolerantReader(new ObjectMapper()), Clock.systemUTC());
        profiles = new OperaProfiles(client, properties, new ObjectMapper());
    }

    @AfterEach
    void stop() {
        ohip.stop(0);
    }

    static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body, String location) throws java.io.IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (location != null) {
            exchange.getResponseHeaders().add("Location", location);
        }
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    static Partner partner() {
        return new Partner("TEST-ERP-0001", PartnerType.TRAVEL_AGENT, "Agencia de prueba", null, null, null, null,
                BillingMode.FRONT, true, 3);
    }

    @Test
    void aPartnerOperaHasIsFoundByItsCorporateId() {
        searchAnswer = """
                {"profileSummaries":{"profileInfo":[
                  {"profileIdList":[{"type":"Profile","id":"16120675"},{"type":"CorporateId","id":"TEST-ERP-0001"}]}]}}""";

        assertThat(profiles.byCorporateId("XMAR", "TEST-ERP-0001", "Agent")).contains("16120675");
        assertThat(calls).singleElement().asString().contains("corporateIds=TEST-ERP-0001").contains("profileType=Agent");
    }

    @Test
    void aProfileWhoseCorporateIdIsAnotherIsNotThePartner() {
        searchAnswer = """
                {"profileSummaries":{"profileInfo":[
                  {"profileIdList":[{"type":"Profile","id":"1"},{"type":"CorporateId","id":"SOMEONE-ELSE"}]}]}}""";

        assertThat(profiles.byCorporateId("XMAR", "TEST-ERP-0001", "Agent")).isEmpty();
    }

    @Test
    void aPartnerOperaDoesNotHaveIsCreatedWithItsCodeAsCorporateIdAndNoExternalReference() {
        var ensured = profiles.ensurePartner("XMAR", partner(), "Agent");

        assertThat(ensured.profileId()).isEqualTo("20600001");
        assertThat(ensured.created()).isTrue();
        assertThat(calls).singleElement().asString().startsWith("POST /crm/v1/profiles")
                .contains("\"profileType\":\"Agent\"").contains("\"companyName\":\"Agencia de prueba\"")
                .contains("{\"id\":\"TEST-ERP-0001\",\"type\":\"CorporateId\"}")
                .doesNotContain("externalReferences");
    }
}
