package io.mateu.ecdemo1.users.application.query;

import io.mateu.ecdemo1.users.application.query.dto.UserDto;
import io.mateu.ecdemo1.users.application.query.dto.UserRow;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.UserId;

public interface UserQueryService extends QueryService<UserDto, UserRow, String> {
}
