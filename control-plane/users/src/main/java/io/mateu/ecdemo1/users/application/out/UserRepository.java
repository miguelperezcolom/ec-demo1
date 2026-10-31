package io.mateu.ecdemo1.users.application.out;

import io.mateu.ecdemo1.users.domain.aggregates.user.User;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.UserId;

import java.util.List;

public interface UserRepository extends Repository<User, UserId> {

    /**
     * Every user. For the employee-directory snapshot, which republishes them all; a few hundred in
     * the demo, so loading them at once is fine — a real directory would page.
     */
    List<User> findAll();
}
