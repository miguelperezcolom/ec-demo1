package io.mateu.ecdemo1.contenthub.domain.employee;

import java.util.Map;

/**
 * An employee as the directory knows them: a projection written only by the consumer of the users
 * service's events — almost a read model. Who they are and what they may do is the users service's
 * business; here only what the visibility function, the newsletter and the intranet's directory need —
 * the last also who they report to ({@code managerId}, a user id), their work phone, both optional, and
 * the labels of their attribute codes ({@code hotel → "Riu Demo Tenerife"}) as the users service names them.
 */
public record Employee(String userId, String name, String email, boolean enabled, EmployeeAttributes attributes,
                       String managerId, String phone, Map<String, String> labels) {

    public Employee {
        labels = labels == null ? Map.of() : Map.copyOf(labels);
    }

    public Employee(String userId, String name, String email, boolean enabled, EmployeeAttributes attributes) {
        this(userId, name, email, enabled, attributes, null, null, null);
    }

    /** The label of an attribute's code ({@code "hotel"}, {@code "jobTitle"}…), or the code, or "". */
    public String label(String key, String code) {
        return code == null ? "" : labels.getOrDefault(key, code);
    }

    public String hash() {
        return attributes.hash();
    }
}
