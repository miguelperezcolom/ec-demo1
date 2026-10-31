package io.mateu.ecdemo1.contenthub.domain.employee;

/**
 * An employee as the directory knows them: a projection written only by the consumer of the users
 * service's events — almost a read model. Who they are and what they may do is the users service's
 * business; here only what the visibility function and the newsletter need.
 */
public record Employee(String userId, String name, String email, boolean enabled, EmployeeAttributes attributes) {

    public String hash() {
        return attributes.hash();
    }
}
