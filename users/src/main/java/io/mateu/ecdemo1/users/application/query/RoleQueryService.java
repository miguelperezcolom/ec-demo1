package io.mateu.ecdemo1.users.application.query;

import io.mateu.ecdemo1.users.application.query.dto.RoleDto;
import io.mateu.ecdemo1.users.application.query.dto.RoleRow;
import io.mateu.ecdemo1.users.domain.aggregates.role.vo.RoleId;

public interface RoleQueryService extends QueryService<RoleDto, RoleRow, String> {
}
