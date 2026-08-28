package io.mateu.ecdemo1.users.application.out;

import io.mateu.ecdemo1.users.domain.aggregates.usergroup.UserGroup;
import io.mateu.ecdemo1.users.domain.aggregates.usergroup.vo.UserGroupId;

public interface UserGroupRepository extends Repository<UserGroup, UserGroupId> {
}
