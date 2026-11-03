package io.mateu.ecdemo1.contenthub.application.directory;

import java.util.Map;

/**
 * What the users service says on employee-directory: an employee upserted (with their attributes as
 * catalogue codes, and who they report to and their phone when the users service knows them) or removed.
 */
public record EmployeeMessage(String eventId, String type, String userId, String name, String email, Boolean enabled,
                              String managerId, String phone, String photoUrl, Map<String, String> attributes,
                              Map<String, String> labels, String occurredAt) {

    /** Without a photo. */
    public EmployeeMessage(String eventId, String type, String userId, String name, String email, Boolean enabled,
                           String managerId, String phone, Map<String, String> attributes,
                           Map<String, String> labels, String occurredAt) {
        this(eventId, type, userId, name, email, enabled, managerId, phone, null, attributes, labels, occurredAt);
    }

    public static final String UPSERTED = "EmployeeUpserted";
    public static final String REMOVED = "EmployeeRemoved";
}
