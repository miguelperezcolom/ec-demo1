package io.mateu.ecdemo1.pmsintegration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.integration.model.integration.IntegrationView;
import io.mateu.ecdemo1.integration.model.integration.OhipConnection;
import io.mateu.ecdemo1.integration.model.reservation.GuestType;
import io.mateu.ecdemo1.integration.model.reservation.Person;
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
 * A guest profile rewritten keeps one email and one phone: the new ones go on the ids of the entries
 * they replace — OPERA adds an entry sent without its id, and cannot delete one.
 */
class GuestProfilesTest {

    HttpServer ohip;
    final List<String> calls = new CopyOnWriteArrayList<>();
    static final String CURRENT = """
            {"profileDetails":{"emails":{"emailInfo":[
              {"email":{"emailAddress":"old@example.com","type":"EMAIL","primaryInd":true},"id":"21898821"},
              {"email":{"emailAddress":"older@example.com","type":"EMAIL","primaryInd":false},"id":"21898215"}]},
             "telephones":{"telephoneInfo":[
              {"telephone":{"phoneTechType":"PHONE","phoneUseType":"MOBILE","phoneNumber":"+34 600 000 000","primaryInd":true},"id":"21898819","type":"MOBILE"}]}}}""";
    volatile String searchAnswer = CURRENT;
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

    static Person ana(String email, String phone) {
        return new Person("Ana", "García", GuestType.ADULT, null, email, phone, "ES", null, null, null);
    }

    @Test
    void aProfileRewrittenChangesItsEmailAndPhoneInPlace() {
        var ensured = profiles.ensureGuest("XMAR", "CU838F", ana("new@example.com", "+34 600 111 222"), "C-1", "20538296");

        assertThat(ensured.profileId()).isEqualTo("20538296");
        assertThat(calls).first().asString().startsWith("GET /crm/v1/profiles/20538296").contains("fetchInstructions=Communication");
        var put = calls.get(1);
        assertThat(put).startsWith("PUT /crm/v1/profiles/20538296")
                .contains("\"id\":\"21898821\"").contains("\"emailAddress\":\"new@example.com\"")
                .contains("\"id\":\"21898819\"").contains("\"phoneNumber\":\"+34 600 111 222\"").contains("\"phoneUseType\":\"MOBILE\"")
                // one entry each: the primary, changed — not a second one next to it
                .doesNotContain("21898215");
    }

    @Test
    void aNewProfileHasNothingToReadFirst() {
        var ensured = profiles.ensureGuest("XMAR", "NEW1", ana("ana@example.com", null), "C-2", null);

        assertThat(ensured.created()).isTrue();
        assertThat(calls).singleElement().asString().startsWith("POST /crm/v1/profiles").doesNotContain("\"id\":");
    }
}
