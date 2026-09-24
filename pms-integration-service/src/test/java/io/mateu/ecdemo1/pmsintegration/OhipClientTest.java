package io.mateu.ecdemo1.pmsintegration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.integration.model.integration.IntegrationView;
import io.mateu.ecdemo1.integration.model.integration.OhipConnection;
import io.mateu.ecdemo1.integration.model.integration.PmsProperty;
import io.mateu.ecdemo1.pmsintegration.config.OhipProperties;
import io.mateu.ecdemo1.pmsintegration.connections.Connections;
import io.mateu.ecdemo1.pmsintegration.config.TolerantReader;
import io.mateu.ecdemo1.pmsintegration.ohip.OhipClient;
import io.mateu.ecdemo1.pmsintegration.ohip.PmsRejectedException;
import io.mateu.ecdemo1.pmsintegration.ohip.PmsTransientException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** The OHIP door: tokens, headers, and how each kind of answer is sorted. */
class OhipClientTest {

    record Answer(int status, String body) {
    }

    HttpServer ohip;
    final Deque<Answer> answers = new ArrayDeque<>();
    final List<String> calls = new ArrayList<>();
    int tokensIssued;
    OhipClient client;
    final Map<String, OhipConnection> connections = new HashMap<>();

    @BeforeEach
    void start() throws Exception {
        ohip = HttpServer.create(new InetSocketAddress(0), 0);
        ohip.createContext("/oauth/v1/tokens", exchange -> {
            tokensIssued++;
            calls.add("TOKEN app=" + exchange.getRequestHeaders().getFirst("x-app-key")
                    + " ent=" + exchange.getRequestHeaders().getFirst("enterpriseId"));
            reply(exchange, 200, "{\"access_token\":\"t" + tokensIssued + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        });
        ohip.createContext("/", exchange -> {
            calls.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " hotel=" + exchange.getRequestHeaders().getFirst("x-hotelid")
                    + " auth=" + exchange.getRequestHeaders().getFirst("Authorization"));
            var answer = answers.isEmpty() ? new Answer(200, "{}") : answers.poll();
            reply(exchange, answer.status(), answer.body());
        });
        ohip.start();
        var gateway = "http://localhost:" + ohip.getAddress().getPort();
        connections.put("RIUPMI", new OhipConnection("RIUPMI", gateway, "app", "id", "secret", "RIUE"));
        connections.put("RIUCUN", new OhipConnection("RIUCUN", gateway, "app2", "id2", "secret2", "RIUE"));
        client = clientWith(new OhipProperties(null, null, null, Duration.ofSeconds(2), null, null, null, null));
    }

    OhipClient clientWith(OhipProperties properties) {
        return new OhipClient(new Connections() {
            @Override
            public Optional<OhipConnection> of(String pmsHotelCode) {
                return Optional.ofNullable(connections.get(pmsHotelCode));
            }

            @Override
            public List<IntegrationView> integrations() {
                return List.of();
            }
        }, properties, new TolerantReader(new ObjectMapper()), Clock.systemUTC());
    }

    @AfterEach
    void stop() {
        ohip.stop(0);
    }

    static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void oneTokenServesManyCallsAndEveryCallCarriesTheHotelAndTheBearer() {
        client.get("RIUPMI", "/rm/config/v1/hotels/{h}/roomTypes", "RIUPMI");
        client.get("RIUPMI", "/rtp/v1/hotels/{h}/ratePlans", "RIUPMI");

        assertThat(tokensIssued).isEqualTo(1);
        assertThat(calls).first().isEqualTo("TOKEN app=app ent=RIUE");
        assertThat(calls.subList(1, 3)).allMatch(c -> c.contains("hotel=RIUPMI") && c.contains("auth=Bearer t1"));
    }

    @Test
    void eachPropertyIsReachedWithItsOwnIntegrationsCredentials() {
        client.get("RIUPMI", "/x");
        client.get("RIUCUN", "/x");
        client.get("RIUPMI", "/y");

        assertThat(tokensIssued).isEqualTo(2);
        assertThat(calls).filteredOn(c -> c.startsWith("TOKEN")).containsExactly("TOKEN app=app ent=RIUE", "TOKEN app=app2 ent=RIUE");
        assertThatThrownBy(() -> client.get("RIUNOPE", "/x")).isInstanceOf(PmsTransientException.class)
                .hasMessageContaining("No integration");
    }

    @Test
    void aConnectionIsTriedBeforeAnyoneReliesOnIt() {
        answers.add(new Answer(200, "{\"roomTypesSummary\":[{\"roomTypeSummary\":[{},{}]}]}"));
        var ok = client.verify(connections.get("RIUPMI"));
        assertThat(ok.ok()).isTrue();
        assertThat(ok.message()).contains("RIUPMI").contains("2 room types");

        answers.add(new Answer(403, "{\"title\":\"User is not authorized to access data for resort.\"}"));
        var refused = client.verify(connections.get("RIUPMI"));
        assertThat(refused.ok()).isFalse();
        assertThat(refused.message()).contains("403").contains("not authorized");

        var nowhere = client.verify(new OhipConnection("RIUPMI", "http://localhost:1", "a", "b", "c", "d"));
        assertThat(nowhere.ok()).isFalse();
    }

    @Test
    void theChainsPropertiesAreAskedOfTheEnterpriseNotOfAHotel() {
        answers.add(new Answer(200, """
                {"hotels":[{"hotelId":"RIUPMI","hotelName":"Riu Demo Palma","currencyCode":"EUR"},
                           {"hotelId":"RIUNEW","hotelName":"Riu Demo Nuevo"}]}"""));

        var properties = client.properties(connections.get("RIUPMI"));

        assertThat(properties).extracting(PmsProperty::code, PmsProperty::name)
                .containsExactly(tuple("RIUPMI", "Riu Demo Palma"), tuple("RIUNEW", "Riu Demo Nuevo"));
        assertThat(calls).last().asString().contains("/ent/config/v1/hotels").contains("hotel=null");
    }

    @Test
    void knownPropertiesAreReadOneByOneAndThoseItCannotReadAreLeftOut() {
        var known = clientWith(new OhipProperties(null, null, null, Duration.ofSeconds(2), null, null, null, List.of("XMAR", "XOTHER")));
        answers.add(new Answer(200, "{\"hotelConfigInfo\":{\"hotelId\":\"XMAR\",\"hotelName\":\"Piloto Mauricio\"}}"));
        answers.add(new Answer(403, "{\"title\":\"User is not authorized to access data for resort.\"}"));

        var properties = known.properties(connections.get("RIUPMI"));

        assertThat(properties).extracting(PmsProperty::code, PmsProperty::name)
                .containsExactly(tuple("XMAR", "Piloto Mauricio"));
        assertThat(calls).noneMatch(call -> call.contains("/ent/config/v1/hotels hotel="));
        assertThat(calls).anyMatch(call -> call.contains("/ent/config/v1/hotels/XMAR hotel=XMAR"));
    }

    @Test
    void aTokenOperaTurnsDownIsRenewedOnce() {
        answers.add(new Answer(401, "{\"title\":\"Invalid token\"}"));
        answers.add(new Answer(200, "{\"ok\":true}"));

        assertThat(client.get("RIUPMI", "/x").body().path("ok").asBoolean()).isTrue();
        assertThat(tokensIssued).isEqualTo(2);
    }

    @Test
    void notNowIsTransientAndNoIsARefusalWithOperasOwnCode() {
        answers.add(new Answer(503, "{\"title\":\"Service Unavailable\"}"));
        assertThatThrownBy(() -> client.get("RIUPMI", "/x")).isInstanceOf(PmsTransientException.class);

        answers.add(new Answer(429, "{}"));
        assertThatThrownBy(() -> client.get("RIUPMI", "/x")).isInstanceOf(PmsTransientException.class);

        answers.add(new Answer(400, "{\"detail\":\"Invalid room type DBL\",\"o:errorCode\":\"OPERAWS-RSV-1\"}"));
        assertThatThrownBy(() -> client.get("RIUPMI", "/x")).isInstanceOfSatisfying(PmsRejectedException.class, e -> {
            assertThat(e.getMessage()).isEqualTo("Invalid room type DBL");
            assertThat(e.errorCode()).isEqualTo("OPERAWS-RSV-1");
        });

        answers.add(new Answer(403, "{\"title\":\"User is not authorized to access data for resort.\",\"o:errorCode\":\"OPERAWS-GEN01244\"}"));
        assertThatThrownBy(() -> client.get("RIUPMI", "/x")).isInstanceOf(PmsRejectedException.class);
    }

    @Test
    void notThereIsAnAnswerNotAFailure() {
        answers.add(new Answer(404, "{\"title\":\"Not found\"}"));
        assertThat(client.find("RIUPMI", "/crm/v1/externalSystems/RIUCRS/profiles/X")).isEmpty();
    }

    @Test
    void anUnreachableOperaIsTransient() {
        ohip.stop(0);
        assertThatThrownBy(() -> client.get("RIUPMI", "/x")).isInstanceOf(PmsTransientException.class);
    }
}
