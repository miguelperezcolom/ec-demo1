package io.mateu.ecdemo1.contenthub.domain.employee;

/**
 * An employee as the directory knows them: a projection written only by the consumer of the users
 * service's events — almost a read model. Who they are and what they may do is the users service's
 * business; here only what the visibility function, the newsletter and the intranet's directory need —
 * the last also who they report to ({@code managerId}, a user id) and their work phone, both optional.
 */
public record Employee(String userId, String name, String email, boolean enabled, EmployeeAttributes attributes,
                       String managerId, String phone) {

    public Employee(String userId, String name, String email, boolean enabled, EmployeeAttributes attributes) {
        this(userId, name, email, enabled, attributes, null, null);
    }

    public String hash() {
        return attributes.hash();
    }
}
