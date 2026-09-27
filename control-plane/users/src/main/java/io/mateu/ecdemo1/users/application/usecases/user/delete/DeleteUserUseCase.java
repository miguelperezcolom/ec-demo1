package io.mateu.ecdemo1.users.application.usecases.user.delete;

import io.mateu.ecdemo1.users.application.out.UserRepository;
import io.mateu.ecdemo1.users.application.usecases.user.identity.IdentityOutboxAppender;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteUserUseCase {

    final UserRepository repository;
    final IdentityOutboxAppender identityOutboxAppender;

    @Transactional
    public void handle(DeleteUserCommand command) {
        var ids = command.ids().stream().map(UserId::new).toList();
        // Raise the deletion event and drain it into this same transaction, before the row goes, so
        // "user gone from here" and "tell Keycloak to remove it" commit together. A user we cannot
        // find is one already gone — nothing to propagate, so it is skipped.
        for (var id : ids) {
            repository.findById(id).ifPresent(user -> {
                user.markDeleted();
                identityOutboxAppender.drain(user);
            });
        }
        repository.deleteAllById(ids);
    }

}
