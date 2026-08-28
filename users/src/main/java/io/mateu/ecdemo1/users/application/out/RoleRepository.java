package io.mateu.ecdemo1.users.application.out;

import io.mateu.ecdemo1.users.domain.aggregates.role.Role;
import io.mateu.ecdemo1.users.domain.aggregates.role.vo.RoleId;

public interface RoleRepository extends Repository<Role, RoleId> {
}
