package io.mateu.ecdemo1.users.application.usecases.user.create;

import io.mateu.ecdemo1.users.application.out.UserRepository;
import io.mateu.ecdemo1.users.domain.aggregates.role.vo.RoleId;
import io.mateu.ecdemo1.users.domain.aggregates.shared.vo.Email;
import io.mateu.ecdemo1.users.domain.aggregates.shared.vo.Name;
import io.mateu.ecdemo1.users.domain.aggregates.user.User;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.UserId;
import io.mateu.ecdemo1.users.domain.aggregates.usergroup.vo.UserGroupId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateUserUseCase {

    final UserRepository repository;

    @Transactional
    public void handle(CreateUserCommand command) {
        repository.save(User.of(
                new UserId(command.id()),
                new Name(command.name()),
                new Email(command.email()),
                command.groupIds().stream().map(UserGroupId::new).toList(),
                command.roleIds().stream().map(RoleId::new).toList()
        ));
    }

}
