package io.mateu.ecdemo1.users.application.usecases.user.directory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeAttributes;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeProfile;

/**
 * What goes out on the {@code employee-directory} topic, keyed by the user id: the contract the
 * content service's directory consumes. Two types —
 *
 * <pre>{@code
 * {"eventId":"…","type":"EmployeeUpserted","userId":"ana","name":"Ana Pérez","email":"ana@example.com",
 *  "enabled":true,"managerId":"luis","phone":"+34 600 000 101","attributes":{"company":"…","country":"ES","region":"CANARIAS","hotel":"TFS01",
 *  "department":"RECEPCION","jobTitle":"RECEPCIONISTA","language":"es"},"occurredAt":"2026-10-31T10:00:00Z"}
 * {"eventId":"…","type":"EmployeeRemoved","userId":"ana","occurredAt":"…"}
 * }</pre>
 *
 * <p>{@code managerId} (who they report to, a user id) and {@code phone} are for the intranet's
 * directory — its org chart and a person's card — and were added later: both optional, left out when
 * unset, so a consumer that does not know them reads the message as before.
 *
 * <p>{@code labels} names the attribute codes as people read them ({@code "hotel":"Riu Demo Tenerife"},
 * {@code "jobTitle":"Jefe de recepción"}), from the catalogue when the message is written: what the
 * directory shows on a card, so the consumer needs no copy of the catalogue. Optional too; a label
 * edited later reaches the consumer with the employee's next message or snapshot.
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
@JsonPropertyOrder({"eventId", "type", "userId", "name", "email", "enabled", "managerId", "phone", "photoUrl", "attributes",
        "labels", "occurredAt"})
public record EmployeeDirectoryMessage(String eventId,
                                       String type,
                                       String userId,
                                       String name,
                                       String email,
                                       Boolean enabled,
                                       String managerId,
                                       String phone,
                                       String photoUrl,
                                       Attributes attributes,
                                       Map<String, String> labels,
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
                                                    EmployeeProfile profile, String occurredAt) {
        var p = profile == null ? EmployeeProfile.NONE : profile;
        return new EmployeeDirectoryMessage(eventId, UPSERTED, userId, name, email, enabled, p.managerId(),
                p.phone(), p.photoUrl(), Attributes.of(attributes), null, occurredAt);
    }

    public static EmployeeDirectoryMessage removed(String eventId, String userId, String occurredAt) {
        return new EmployeeDirectoryMessage(eventId, REMOVED, userId, null, null, null, null, null, null, null, null,
                occurredAt);
    }

    /** The same message, with the attribute codes' labels (left out when there are none). */
    public EmployeeDirectoryMessage withLabels(Map<String, String> labels) {
        return new EmployeeDirectoryMessage(eventId, type, userId, name, email, enabled, managerId, phone, photoUrl, attributes,
                labels == null || labels.isEmpty() ? null : labels, occurredAt);
    }
}
