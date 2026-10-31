package io.mateu.ecdemo1.contenthub.application.security;

import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;

import java.util.Set;

/**
 * Who is asking: their id (the token's username, the users service's UserId), their attributes from the
 * directory, and their editorial permissions.
 *
 * @param viewingAs when an editor previews "ver como": the profile they look through, else null
 */
public record Reader(String userId, String name, EmployeeAttributes attributes, Set<EditorialPermission> permissions,
                     String viewingAs) {

    public boolean may(EditorialPermission permission) {
        return permissions.contains(permission) || permissions.contains(EditorialPermission.ADMIN);
    }

    public void require(EditorialPermission permission) {
        if (!may(permission)) {
            throw new SecurityException("Hace falta el permiso " + permission.scope());
        }
    }
}
