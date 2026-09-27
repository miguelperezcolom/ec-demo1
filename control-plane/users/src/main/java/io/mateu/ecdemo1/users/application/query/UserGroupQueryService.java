package io.mateu.ecdemo1.users.application.query;

import io.mateu.ecdemo1.users.application.query.dto.UserGroupDto;
import io.mateu.ecdemo1.users.application.query.dto.UserGroupRow;
import io.mateu.ecdemo1.users.domain.aggregates.usergroup.vo.UserGroupId;

public interface UserGroupQueryService extends QueryService<UserGroupDto, UserGroupRow, String> {
}
