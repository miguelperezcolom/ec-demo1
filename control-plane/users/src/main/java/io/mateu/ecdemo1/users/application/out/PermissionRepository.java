package io.mateu.ecdemo1.users.application.out;

import io.mateu.ecdemo1.users.domain.aggregates.permission.Permission;
import io.mateu.ecdemo1.users.domain.aggregates.permission.vo.PermissionId;
import io.mateu.ecdemo1.users.domain.aggregates.permission.vo.Scope;

import java.util.Optional;

public interface PermissionRepository extends Repository<Permission, PermissionId> {

    /**
     * The permission granting a scope, if there is one. Permissions are keyed by a generated number,
     * so the scope is the only stable handle a seed can find its own by on a later start.
     */
    Optional<PermissionId> findIdByScope(Scope scope);
}
