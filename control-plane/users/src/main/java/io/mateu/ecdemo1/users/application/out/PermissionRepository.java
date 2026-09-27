package io.mateu.ecdemo1.users.application.out;

import io.mateu.ecdemo1.users.domain.aggregates.permission.Permission;
import io.mateu.ecdemo1.users.domain.aggregates.permission.vo.PermissionId;

public interface PermissionRepository extends Repository<Permission, PermissionId> {
}
