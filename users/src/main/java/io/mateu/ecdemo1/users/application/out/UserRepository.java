package io.mateu.ecdemo1.users.application.out;

import io.mateu.ecdemo1.users.domain.aggregates.user.User;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.UserId;

public interface UserRepository extends Repository<User, UserId> {
}
