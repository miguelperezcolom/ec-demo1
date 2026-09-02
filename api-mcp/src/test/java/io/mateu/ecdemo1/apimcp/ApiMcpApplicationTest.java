package io.mateu.ecdemo1.apimcp;

import io.mateu.ecdemo1.apimcp.server.ApiMcpServers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * That the whole thing starts, and starts with nothing mounted.
 *
 * <p>Worth a test of its own because of the one unusual thing in this service: the endpoints are a
 * bean that Spring collects ONCE, serving a set of routes that does not exist when it is created.
 * If {@code ApiMcpRouting} ever stops being a lambda over a live registry — if someone
 * "simplifies" it into a router built from what is mounted at startup — everything still compiles
 * and the context still loads, and this is where it shows: nothing would ever be reachable.
 *
 * <p>The control plane is deliberately not there. A pod that cannot read the catalogue has to come
 * up, say so through its readiness probe, and keep polling; a startup that depended on another
 * service being up would make a cold cluster start in an order nobody controls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "api-mcp.control-plane.url=http://127.0.0.1:1",
                // Off. This test is about the context, and a poll racing the assertions would
                // make it flaky about something it is not testing.
                "api-mcp.refresh.initial-delay=1h",
        })
class ApiMcpApplicationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ApiMcpServers servers;

    @Test
    void starts_with_no_endpoints_when_the_catalogue_cannot_be_read() {
        assertThat(servers.endpointCount()).isZero();
    }

    @Test
    void reports_itself_not_ready_until_the_catalogue_has_been_read_once() {
        // Not "the control plane is down": before the first read there is genuinely nothing to
        // serve, and a pod answering 404 for every endpoint an operator expects is worse than one
        // that says it is not ready.
        var response = rest.getForEntity("/actuator/health/readiness", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void answers_404_for_an_endpoint_no_catalogue_entry_mounted() {
        var response = rest.getForEntity("/not-a-catalogued-api/sse", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
