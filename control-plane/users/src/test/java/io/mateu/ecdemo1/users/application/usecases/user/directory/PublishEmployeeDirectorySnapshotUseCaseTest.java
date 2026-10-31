package io.mateu.ecdemo1.users.application.usecases.user.directory;

import io.mateu.ecdemo1.messaging.Inbox;
import io.mateu.ecdemo1.messaging.Outbox;
import io.mateu.ecdemo1.users.application.out.UserRepository;
import io.mateu.ecdemo1.users.domain.aggregates.shared.vo.Email;
import io.mateu.ecdemo1.users.domain.aggregates.shared.vo.Name;
import io.mateu.ecdemo1.users.domain.aggregates.user.User;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeAttributes;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.UserId;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishEmployeeDirectorySnapshotUseCaseTest {

    final Inbox inbox = mock(Inbox.class);
    final Outbox outbox = mock(Outbox.class);
    final Set<String> seen = new HashSet<>();

    static User user(String id) {
        var user = User.of(new UserId(id), new Name(id), new Email(id + "@example.com"), List.of(), List.of(),
                new EmployeeAttributes(null, "ES", "CANARIAS", "TFS01", "RECEPCION", null, "es"));
        user.popEvents();
        return user;
    }

    final UserRepository users = new UserRepository() {
        @Override
        public List<User> findAll() {
            return List.of(user("ana"), user("carlos"));
        }

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.empty();
        }

        @Override
        public UserId save(User entity) {
            return entity.getId();
        }

        @Override
        public void deleteAllById(List<UserId> selectedIds) {
        }
    };

    final PublishEmployeeDirectorySnapshotUseCase snapshot =
            new PublishEmployeeDirectorySnapshotUseCase(inbox, users, new EmployeeDirectoryOutboxAppender(outbox));

    {
        when(inbox.firstTime(eq("employee-directory-requests"), anyString()))
                .thenAnswer(call -> seen.add(call.getArgument(1)));
    }

    @Test
    void everyUserIsRepublishedAsAnUpsertKeyedApartFromIdentity() {
        assertThat(snapshot.handle("r-1", "content-hub")).isEqualTo(2);

        verify(outbox).append(eq("employee-directory"), eq("employee-directory/ana"), eq("EmployeeUpserted"),
                contains("\"region\":\"CANARIAS\""), any());
        verify(outbox).append(eq("employee-directory"), eq("employee-directory/carlos"), eq("EmployeeUpserted"),
                contains("\"userId\":\"carlos\""), any());
    }

    @Test
    void aRepeatedRequestIsAnsweredOnce() {
        snapshot.handle("r-1", "content-hub");
        assertThat(snapshot.handle("r-1", "content-hub")).isEqualTo(-1);

        verify(outbox, times(2)).append(eq("employee-directory"), anyString(), anyString(), anyString(), any());
    }

    @Test
    void aRequestWithoutIdIsRefusedBeforeAnything() {
        assertThatThrownBy(() -> snapshot.handle(" ", "content-hub")).isInstanceOf(IllegalArgumentException.class);
        verify(outbox, never()).append(anyString(), anyString(), anyString(), anyString(), any());
    }
}
