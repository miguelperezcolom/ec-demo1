package io.mateu.ecdemo1.users.application.usecases.user.save;

import io.mateu.ecdemo1.users.application.out.UserRepository;
import io.mateu.ecdemo1.users.application.usecases.user.attributes.EmployeeAttributesResolver;
import io.mateu.ecdemo1.users.application.usecases.user.publish.UserChangePublisher;
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
    final UserChangePublisher publisher;
    final EmployeeAttributesResolver attributesResolver;
    final io.mateu.ecdemo1.users.application.usecases.user.people.ReportingLine reportingLine;

    @Transactional
    public void handle(SaveUserCommand command) {
        reportingLine.check(command.id(), command.profile());
        var user = repository.findById(new UserId(command.id())).orElseThrow();
        user.update(new Name(command.name()),
                new Email(command.email()),
                command.groups().stream().map(UserGroupId::new).toList(),
                command.roles().stream().map(RoleId::new).toList(),
                attributesResolver.resolve(command.attributes()),
                // a photo changes only through PersonPhotos: no form holds it
                (command.profile() == null ? io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeProfile.NONE : command.profile())
                        .withPhotoUrl(user.getProfile() == null ? null : user.getProfile().photoUrl())
                );
        repository.save(user);
        // Same transaction as the save: the change to propagate commits with the user. See
        // UserChangePublisher.
        publisher.publish(user);
    }

}
