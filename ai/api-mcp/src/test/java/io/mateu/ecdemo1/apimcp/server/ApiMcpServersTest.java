package io.mateu.ecdemo1.apimcp.server;

import io.mateu.ecdemo1.apimcp.call.ApiCaller;
import io.mateu.ecdemo1.apimcp.catalogue.CataloguedApi;
import io.mateu.ecdemo1.apimcp.catalogue.CatalogueClient;
import io.mateu.ecdemo1.apimcp.spec.SpecResolver;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which catalogue entries become endpoints, and which deliberately do not.
 *
 * <p>The catalogue is stubbed by subclassing the client rather than mocking it: what is being
 * asserted is what this class does with an answer, and a subclass that returns one is the whole
 * of the setup.
 */
class ApiMcpServersTest {

    private static final String SPEC =
            Path.of("src/test/resources/bookings-openapi.yaml").toAbsolutePath().toString();

    /** A catalogue that answers with whatever the test set, without a control plane. */
    private static class StubCatalogue extends CatalogueClient {
        private List<CataloguedApi> apis = List.of();

        StubCatalogue() {
            super("http://localhost:0");
        }

        @Override
        public List<CataloguedApi> fetch() {
            return apis;
        }
    }

    private final StubCatalogue catalogue = new StubCatalogue();
    private final ApiMcpServers servers =
            new ApiMcpServers(catalogue, new SpecResolver(), new ApiCaller());

    private static CataloguedApi api(String id, CataloguedApi.Tool... tools) {
        return new CataloguedApi(id, "Bookings", "REST", "http://booking:8108", SPEC,
                "s3cret", List.of(tools), "The bookings API");
    }

    private static CataloguedApi.Tool tool(String operation, String name, List<String> roles) {
        return new CataloguedApi.Tool(operation, name, "Reads one booking by its id", roles);
    }

    @Test
    void serves_an_entry_whose_tools_resolve_against_its_spec() {
        catalogue.apis = List.of(api("bookings",
                tool("GET /bookings/{id}", "get_booking", List.of())));

        servers.refresh();

        assertThat(servers.endpointCount()).isEqualTo(1);
    }

    @Test
    void refuses_to_serve_a_tool_that_declares_required_roles() {
        // Fails CLOSED, and this is the assertion worth having. The catalogue says these roles are
        // checked where the call is made — here — and this transport hands the handler no HTTP
        // request to read a caller from. Serving it anyway would turn a stated restriction into
        // none at all, silently.
        catalogue.apis = List.of(api("bookings",
                tool("GET /bookings/{id}", "get_booking", List.of("booking-admin"))));

        servers.refresh();

        assertThat(servers.endpointCount()).isZero();
    }

    @Test
    void drops_a_tool_naming_an_operation_the_spec_no_longer_declares() {
        catalogue.apis = List.of(api("bookings",
                tool("GET /bookings/{id}", "get_booking", List.of()),
                tool("DELETE /bookings/{id}", "delete_booking", List.of())));

        servers.refresh();

        // The entry is still served — losing one tool degrades it, and refusing the whole API
        // because one operation was removed would take the working tools down with it.
        assertThat(servers.endpointCount()).isEqualTo(1);
    }

    @Test
    void does_not_serve_a_kind_it_cannot_call() {
        // A SOAP entry is allowed to exist in the catalogue before anything can read a WSDL. An
        // endpoint that answered with no tools would look like a working server offering nothing.
        catalogue.apis = List.of(new CataloguedApi("legacy", "Legacy", "SOAP",
                "http://legacy", SPEC, null,
                List.of(tool("GET /bookings/{id}", "get_booking", List.of())), null));

        servers.refresh();

        assertThat(servers.endpointCount()).isZero();
    }

    @Test
    void leaves_an_unchanged_entry_alone_across_polls() {
        // A remount drops every connected agent's session. A poll that finds nothing changed has
        // to do nothing at all, and the signature is what decides that.
        catalogue.apis = List.of(api("bookings",
                tool("GET /bookings/{id}", "get_booking", List.of())));

        servers.refresh();
        servers.refresh();
        servers.refresh();

        assertThat(servers.mountsPerformed()).isEqualTo(1);
        assertThat(servers.endpointCount()).isEqualTo(1);
    }

    @Test
    void rebuilds_an_entry_whose_offer_changed() {
        catalogue.apis = List.of(api("bookings",
                tool("GET /bookings/{id}", "get_booking", List.of())));
        servers.refresh();

        catalogue.apis = List.of(api("bookings",
                tool("GET /bookings/{id}", "get_booking", List.of()),
                tool("POST /bookings", "create_booking", List.of())));
        servers.refresh();

        assertThat(servers.mountsPerformed()).isEqualTo(2);
        assertThat(servers.endpointCount()).isEqualTo(1);
    }

    @Test
    void unmounts_an_entry_that_left_the_catalogue() {
        catalogue.apis = List.of(api("bookings",
                tool("GET /bookings/{id}", "get_booking", List.of())));
        servers.refresh();

        catalogue.apis = List.of();
        servers.refresh();

        assertThat(servers.endpointCount()).isZero();
    }
}
