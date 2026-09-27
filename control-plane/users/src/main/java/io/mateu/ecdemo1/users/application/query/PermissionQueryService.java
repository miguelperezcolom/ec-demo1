package io.mateu.ecdemo1.users.application.query;

import io.mateu.ecdemo1.users.application.query.dto.PermissionDto;
import io.mateu.ecdemo1.users.application.query.dto.PermissionRow;
import io.mateu.ecdemo1.users.domain.aggregates.permission.vo.PermissionId;

public interface PermissionQueryService extends QueryService<PermissionDto, PermissionRow, String> {
}
