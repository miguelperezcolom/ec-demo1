package io.mateu.ecdemo1.users.application.usecases.user.save;

import io.mateu.ecdemo1.users.application.out.UserRepository;
import io.mateu.ecdemo1.users.domain.aggregates.role.vo.RoleId;
import io.mateu.ecdemo1.users.domain.aggregates.shared.vo.Email;
import io.mateu.ecdemo1.users.domain.aggregates.shared.vo.Name;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.UserId;
import io.mateu.ecdemo1.users.domain.aggregates.usergroup.vo.UserGroupId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SaveUserUseCase {

    final UserRepository repository;

    @Transactional
    public void handle(SaveUserCommand command) {
        var user = repository.findById(new UserId(command.id())).orElseThrow();
        user.update(new Name(command.name()),
                new Email(command.email()),
                command.groups().stream().map(UserGroupId::new).toList(),
                command.roles().stream().map(RoleId::new).toList()
                );
        repository.save(user);
    }

}
