package io.mateu.ecdemo1.contenthub.application.directory;

import java.util.Map;

/**
 * What the users service says on employee-directory: an employee upserted (with their attributes as
 * catalogue codes) or removed.
 */
public record EmployeeMessage(String eventId, String type, String userId, String name, String email, Boolean enabled,
                              Map<String, String> attributes, String occurredAt) {

    public static final String UPSERTED = "EmployeeUpserted";
    public static final String REMOVED = "EmployeeRemoved";
}
