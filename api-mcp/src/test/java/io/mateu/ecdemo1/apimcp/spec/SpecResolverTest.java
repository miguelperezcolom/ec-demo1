package io.mateu.ecdemo1.apimcp.spec;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What a model is shown for an operation, and where this API's credential goes.
 *
 * <p>Against a real document read by the real parser rather than a stubbed one: the whole reason
 * this side carries a parser at all is $refs and path-level parameters, and a fixture that avoided
 * both would test the one part that never needed a library.
 */
class SpecResolverTest {

    private final SpecResolver resolver = new SpecResolver();

    private ResolvedSpec spec() {
        return resolver.resolve(Path.of("src/test/resources/bookings-openapi.yaml")
                .toAbsolutePath().toString());
    }

    @Test
    void keys_operations_the_way_an_exposed_tool_records_them() {
        assertThat(spec().operations()).containsKeys("GET /bookings/{id}", "POST /bookings");
    }

    @Test
    void carries_a_parameter_declared_on_the_path_rather_than_on_the_operation() {
        // A spec that declares the id once, above the methods, is common and is exactly the shape
        // that produces a URL with a literal {id} in it when it is missed.
        var operation = spec().operation("GET /bookings/{id}").orElseThrow();

        assertThat(operation.parameters())
                .contains(new ResolvedSpec.Parameter("id", "path", true))
                .contains(new ResolvedSpec.Parameter("verbose", "query", false));
    }

    @Test
    void offers_the_parameters_as_one_flat_object_the_model_fills_in() {
        var operation = spec().operation("GET /bookings/{id}").orElseThrow();

        assertThat(operation.inputSchema()).containsEntry("type", "object");
        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) operation.inputSchema().get("properties");
        assertThat(properties).containsKeys("id", "verbose");
        assertThat(operation.inputSchema().get("required")).isEqualTo(List.of("id"));
    }

    @Test
    void resolves_a_body_that_the_document_only_referenced() {
        // The $ref is the point: an unresolved one describes nothing, and a tool whose body schema
        // describes nothing is a tool the model fills in wrongly every time.
        var operation = spec().operation("POST /bookings").orElseThrow();

        assertThat(operation.hasBody()).isTrue();
        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) operation.inputSchema().get("properties");
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) properties.get("body");
        @SuppressWarnings("unchecked")
        var bodyProperties = (Map<String, Object>) body.get("properties");
        assertThat(bodyProperties).containsKeys("leadName", "status");
        assertThat(operation.inputSchema().get("required")).isEqualTo(List.of("body"));
    }

    @Test
    void keeps_an_enum_so_the_model_does_not_invent_a_status() {
        var operation = spec().operation("POST /bookings").orElseThrow();
        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) operation.inputSchema().get("properties");
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) properties.get("body");
        @SuppressWarnings("unchecked")
        var bodyProperties = (Map<String, Object>) body.get("properties");
        @SuppressWarnings("unchecked")
        var status = (Map<String, Object>) bodyProperties.get("status");

        assertThat(status.get("enum")).isEqualTo(List.of("Pending", "Confirmed", "Cancelled"));
    }

    @Test
    void takes_the_credential_placement_from_the_document_rather_than_assuming_bearer() {
        assertThat(spec().credential())
                .isEqualTo(new ResolvedSpec.CredentialPlacement(
                        ResolvedSpec.CredentialPlacement.Kind.HEADER, "X-Api-Key"));
    }

    @Test
    void says_so_plainly_when_the_document_cannot_be_read() {
        // An operator composed the entry that names this url, so the message is for them.
        assertThatThrownBy(() -> resolver.resolve("file:///nowhere/there-is-no-spec.yaml"))
                .isInstanceOf(SpecResolver.SpecUnreadableException.class);
    }
}
