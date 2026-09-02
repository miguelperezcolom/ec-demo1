package io.mateu.ecdemo1.apimcp.call;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.apimcp.spec.ResolvedSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What actually goes on the wire, asserted against a real HTTP server rather than a mock.
 *
 * <p>Everything this class gets wrong is invisible in a unit test that stubs the client: a path
 * parameter left un-substituted, a space encoded as a plus, a credential in the wrong place. The
 * server here records the request it received, and the assertions are about that.
 */
class ApiCallerTest {

    private final ApiCaller caller = new ApiCaller();
    private HttpServer server;
    private String baseUrl;

    /** The last request the server saw: method, path with query, headers, body. */
    private final AtomicReference<Seen> seen = new AtomicReference<>();

    private record Seen(String method, String uri, Headers headers, String body) {
    }

    private int status = 200;
    private String responseBody = "{\"ok\":true}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::record);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void record(HttpExchange exchange) throws IOException {
        var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        seen.set(new Seen(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                exchange.getRequestHeaders(), body));
        var bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static ResolvedSpec.Operation get(String path, ResolvedSpec.Parameter... parameters) {
        return new ResolvedSpec.Operation("GET", path, List.of(parameters), false,
                Map.of("type", "object"));
    }

    @Test
    void substitutes_a_path_parameter_and_appends_a_query_one() {
        var operation = get("/bookings/{id}",
                new ResolvedSpec.Parameter("id", "path", true),
                new ResolvedSpec.Parameter("verbose", "query", false));

        var outcome = caller.call(baseUrl, null, ResolvedSpec.CredentialPlacement.bearer(),
                operation, Map.of("id", "abc123", "verbose", true));

        assertThat(outcome.ok()).isTrue();
        assertThat(seen.get().uri()).isEqualTo("/bookings/abc123?verbose=true");
    }

    @Test
    void encodes_a_space_in_a_path_as_percent_twenty_and_not_as_a_plus() {
        // Form encoding in a path segment produces a literal plus, which is a different resource.
        var operation = get("/leads/{name}", new ResolvedSpec.Parameter("name", "path", true));

        caller.call(baseUrl, null, ResolvedSpec.CredentialPlacement.bearer(), operation,
                Map.of("name", "Ada Lovelace"));

        assertThat(seen.get().uri()).isEqualTo("/leads/Ada%20Lovelace");
    }

    @Test
    void refuses_before_calling_when_a_required_path_parameter_is_missing() {
        // An un-substituted {id} is a URL for a different thing, and an API answering 200 for the
        // wrong resource is the one failure the model cannot notice.
        var operation = get("/bookings/{id}", new ResolvedSpec.Parameter("id", "path", true));

        var outcome = caller.call(baseUrl, null, ResolvedSpec.CredentialPlacement.bearer(),
                operation, Map.of());

        assertThat(outcome.ok()).isFalse();
        assertThat(outcome.text()).contains("id");
        assertThat(seen.get()).isNull();
    }

    @Test
    void puts_an_api_key_in_the_header_the_spec_named() {
        var placement = new ResolvedSpec.CredentialPlacement(
                ResolvedSpec.CredentialPlacement.Kind.HEADER, "X-Api-Key");

        caller.call(baseUrl, "s3cret", placement, get("/bookings"), Map.of());

        assertThat(seen.get().headers().getFirst("X-api-key")).isEqualTo("s3cret");
    }

    @Test
    void prefixes_a_bare_token_with_bearer_and_leaves_one_that_names_its_scheme_alone() {
        caller.call(baseUrl, "abc", ResolvedSpec.CredentialPlacement.bearer(), get("/a"), Map.of());
        assertThat(seen.get().headers().getFirst("Authorization")).isEqualTo("Bearer abc");

        caller.call(baseUrl, "Basic dXNlcjpwdw==", ResolvedSpec.CredentialPlacement.bearer(),
                get("/a"), Map.of());
        assertThat(seen.get().headers().getFirst("Authorization")).isEqualTo("Basic dXNlcjpwdw==");
    }

    @Test
    void sends_a_body_as_json_when_the_operation_takes_one() {
        var operation = new ResolvedSpec.Operation("POST", "/bookings", List.of(), true,
                Map.of("type", "object"));

        caller.call(baseUrl, null, ResolvedSpec.CredentialPlacement.bearer(), operation,
                Map.of("body", Map.of("leadName", "Ada")));

        assertThat(seen.get().method()).isEqualTo("POST");
        assertThat(seen.get().body()).isEqualTo("{\"leadName\":\"Ada\"}");
        assertThat(seen.get().headers().getFirst("Content-type")).isEqualTo("application/json");
    }

    @Test
    void hands_a_refusal_back_as_a_result_the_model_can_explain() {
        // Not an exception: the model asked to call something and the something said no, and
        // telling it so is the reason a tool call goes through a model at all.
        status = 404;
        responseBody = "{\"error\":\"no such booking\"}";

        var outcome = caller.call(baseUrl, null, ResolvedSpec.CredentialPlacement.bearer(),
                get("/bookings/x"), Map.of());

        assertThat(outcome.ok()).isFalse();
        assertThat(outcome.text()).contains("404").contains("no such booking");
    }

    @Test
    void says_a_call_succeeded_with_nothing_to_read_rather_than_returning_empty() {
        // An empty string reads to a model as a failure worth retrying, and a 204 is not one.
        status = 204;
        responseBody = "";

        var outcome = caller.call(baseUrl, null, ResolvedSpec.CredentialPlacement.bearer(),
                get("/bookings/x"), Map.of());

        assertThat(outcome.ok()).isTrue();
        assertThat(outcome.text()).contains("no content");
    }

    @Test
    void reports_a_call_that_could_not_be_made_as_a_failure_of_this_service() {
        var outcome = caller.call("http://127.0.0.1:1", null,
                ResolvedSpec.CredentialPlacement.bearer(), get("/bookings"), Map.of());

        assertThat(outcome.ok()).isFalse();
        assertThat(outcome.text()).startsWith("The call could not be made");
    }
}
