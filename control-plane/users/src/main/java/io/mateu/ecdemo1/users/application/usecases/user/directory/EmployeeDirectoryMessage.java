package io.mateu.ecdemo1.users.application.usecases.user.directory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeAttributes;

/**
 * What goes out on the {@code employee-directory} topic, keyed by the user id: the contract the
 * content service's directory consumes. Two types —
 *
 * <pre>{@code
 * {"eventId":"…","type":"EmployeeUpserted","userId":"ana","name":"Ana Pérez","email":"ana@example.com",
 *  "enabled":true,"attributes":{"company":"…","country":"ES","region":"CANARIAS","hotel":"TFS01",
 *  "department":"RECEPCION","jobTitle":"RECEPCIONISTA","language":"es"},"occurredAt":"2026-10-31T10:00:00Z"}
 * {"eventId":"…","type":"EmployeeRemoved","userId":"ana","occurredAt":"…"}
 * }</pre>
 *
 * <p>An upsert carries the employee in full, not what changed: a consumer replaces its copy, so a
 * redelivery or a snapshot converges on the same state. A disabled user is still an upsert, with
 * {@code enabled} false — the person exists, and an audience may still want to know them. Absent
 * fields (a removal's name, an attribute nobody set) are left out rather than written as null.
 *
 * <p>Its own record rather than the domain events serialised: the topic is a published contract,
 * and renaming a field in the domain must not silently rename it on the wire.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"eventId", "type", "userId", "name", "email", "enabled", "attributes", "occurredAt"})
public record EmployeeDirectoryMessage(String eventId,
                                       String type,
                                       String userId,
                                       String name,
                                       String email,
                                       Boolean enabled,
                                       Attributes attributes,
                                       String occurredAt) {

    public static final String UPSERTED = "EmployeeUpserted";
    public static final String REMOVED = "EmployeeRemoved";

    /** The attribute values, as catalogue codes. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"company", "country", "region", "hotel", "department", "jobTitle", "language"})
    public record Attributes(String company, String country, String region, String hotel, String department,
                             String jobTitle, String language) {

        static Attributes of(EmployeeAttributes a) {
            var attributes = a == null ? EmployeeAttributes.NONE : a;
            return new Attributes(attributes.company(), attributes.country(), attributes.region(),
                    attributes.hotel(), attributes.department(), attributes.jobTitle(), attributes.language());
        }
    }

    public static EmployeeDirectoryMessage upserted(String eventId, String userId, String name, String email,
                                                    boolean enabled, EmployeeAttributes attributes,
                                                    String occurredAt) {
        return new EmployeeDirectoryMessage(eventId, UPSERTED, userId, name, email, enabled,
                Attributes.of(attributes), occurredAt);
    }

    public static EmployeeDirectoryMessage removed(String eventId, String userId, String occurredAt) {
        return new EmployeeDirectoryMessage(eventId, REMOVED, userId, null, null, null, null, occurredAt);
    }
}
