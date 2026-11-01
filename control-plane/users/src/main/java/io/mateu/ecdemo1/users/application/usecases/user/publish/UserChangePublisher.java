package io.mateu.ecdemo1.users.application.usecases.user.publish;

import io.mateu.ecdemo1.users.application.usecases.user.directory.EmployeeDirectoryOutboxAppender;
import io.mateu.ecdemo1.users.application.usecases.user.identity.IdentityOutboxAppender;
import io.mateu.ecdemo1.users.domain.aggregates.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Drains a saved user's domain events, once, into the two outboxes that hear about users: the identity
 * provider's ({@link IdentityOutboxAppender}) and the employee directory's
 * ({@link EmployeeDirectoryOutboxAppender}).
 *
 * <p>One class pops the events because popping empties them: two appenders each popping would leave
 * the second with nothing. Must run in the use case's transaction, right after the save — that is what
 * makes both messages commit with the user.
 */
@Service
@RequiredArgsConstructor
public class UserChangePublisher {

    final IdentityOutboxAppender identity;
    final EmployeeDirectoryOutboxAppender directory;

    public void publish(User user) {
        publish(user, false);
    }

    /** @param skipPasswordSetupEmail create the provider's user without mailing them a set-password link */
    public void publish(User user, boolean skipPasswordSetupEmail) {
        var events = user.popEvents();
        identity.append(events, skipPasswordSetupEmail);
        directory.append(events);
    }
}
