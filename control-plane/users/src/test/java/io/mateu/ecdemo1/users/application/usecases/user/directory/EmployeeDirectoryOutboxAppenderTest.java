package io.mateu.ecdemo1.users.application.usecases.user.directory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.users.domain.aggregates.user.events.UserCreated;
import io.mateu.ecdemo1.users.domain.aggregates.user.events.UserDeleted;
import io.mateu.ecdemo1.users.domain.aggregates.user.events.UserUpdated;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeAttributes;
import io.mateu.workflow.ddd.DomainEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The {@code employee-directory} contract, as the content service reads it. */
class EmployeeDirectoryOutboxAppenderTest {

    static final ObjectMapper JSON = new ObjectMapper();
    static final EmployeeAttributes ANA = new EmployeeAttributes("RIUDEMO_HOTELES", "ES", "CANARIAS", "TFS01",
            "RECEPCION", "RECEPCIONISTA", "es");

    static JsonNode wire(DomainEvent event) throws Exception {
        var message = EmployeeDirectoryOutboxAppender.toMessage(event, "e-1", "2026-10-31T10:00:00Z").orElseThrow();
        return JSON.readTree(EmployeeDirectoryOutboxAppender.toJson(message));
    }

    @Test
    void aCreatedUserIsAnUpsertWithItsAttributes() throws Exception {
        var json = wire(new UserCreated("ana", "Ana Pérez", "ana@example.com", true, ANA));

        assertThat(json.get("eventId").asText()).isEqualTo("e-1");
        assertThat(json.get("type").asText()).isEqualTo("EmployeeUpserted");
        assertThat(json.get("userId").asText()).isEqualTo("ana");
        assertThat(json.get("name").asText()).isEqualTo("Ana Pérez");
        assertThat(json.get("email").asText()).isEqualTo("ana@example.com");
        assertThat(json.get("enabled").asBoolean()).isTrue();
        assertThat(json.get("occurredAt").asText()).isEqualTo("2026-10-31T10:00:00Z");
        var attributes = json.get("attributes");
        assertThat(attributes.get("company").asText()).isEqualTo("RIUDEMO_HOTELES");
        assertThat(attributes.get("country").asText()).isEqualTo("ES");
        assertThat(attributes.get("region").asText()).isEqualTo("CANARIAS");
        assertThat(attributes.get("hotel").asText()).isEqualTo("TFS01");
        assertThat(attributes.get("department").asText()).isEqualTo("RECEPCION");
        assertThat(attributes.get("jobTitle").asText()).isEqualTo("RECEPCIONISTA");
        assertThat(attributes.get("language").asText()).isEqualTo("es");
    }

    @Test
    void aDisabledUserIsStillAnUpsertAndAbsentAttributesAreLeftOut() throws Exception {
        var json = wire(new UserUpdated("ana", "Ana Pérez", "ana@example.com", false,
                new EmployeeAttributes(null, "ES", null, null, "IT", null, "es")));

        assertThat(json.get("type").asText()).isEqualTo("EmployeeUpserted");
        assertThat(json.get("enabled").asBoolean()).isFalse();
        var attributes = json.get("attributes");
        assertThat(attributes.has("company")).isFalse();
        assertThat(attributes.has("hotel")).isFalse();
        assertThat(attributes.get("department").asText()).isEqualTo("IT");
    }

    @Test
    void aUserWithoutAttributesHasAnEmptyAttributesObject() throws Exception {
        var json = wire(new UserCreated("x", "X", "x@example.com", true, null));

        assertThat(json.get("attributes").isObject()).isTrue();
        assertThat(json.get("attributes").size()).isZero();
    }

    @Test
    void aDeletedUserIsARemovalWithOnlyItsId() throws Exception {
        var json = wire(new UserDeleted("ana"));

        assertThat(json.get("type").asText()).isEqualTo("EmployeeRemoved");
        assertThat(json.get("userId").asText()).isEqualTo("ana");
        assertThat(json.get("eventId").asText()).isEqualTo("e-1");
        assertThat(json.get("occurredAt").asText()).isEqualTo("2026-10-31T10:00:00Z");
        assertThat(json.has("name")).isFalse();
        assertThat(json.has("enabled")).isFalse();
        assertThat(json.has("attributes")).isFalse();
    }

    @Test
    void otherEventsBecomeNothing() {
        assertThat(EmployeeDirectoryOutboxAppender.toMessage(new DomainEvent() {}, "e", "t")).isEmpty();
    }
}
