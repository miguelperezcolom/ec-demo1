package io.mateu.ecdemo1.users.application.usecases.user.publish;

import io.mateu.ecdemo1.messaging.Outbox;
import io.mateu.ecdemo1.users.application.usecases.user.directory.EmployeeDirectoryOutboxAppender;
import io.mateu.ecdemo1.users.application.usecases.user.identity.IdentityOutboxAppender;
import io.mateu.ecdemo1.users.domain.aggregates.shared.vo.Email;
import io.mateu.ecdemo1.users.domain.aggregates.shared.vo.Name;
import io.mateu.ecdemo1.users.domain.aggregates.user.User;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeAttributes;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.UserId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserChangePublisherTest {

    final Outbox outbox = mock(Outbox.class);
    final UserChangePublisher publisher = new UserChangePublisher(new IdentityOutboxAppender(outbox),
            new EmployeeDirectoryOutboxAppender(outbox));

    @Test
    void oneChangeReachesKeycloakWithoutAttributesAndTheDirectoryWithThem() {
        var user = User.of(new UserId("ana"), new Name("Ana Pérez"), new Email("ana@example.com"), List.of(),
                List.of(), new EmployeeAttributes("RIUDEMO_HOTELES", "ES", "CANARIAS", "TFS01", null, null, "es"));

        publisher.publish(user, true);

        var identity = ArgumentCaptor.forClass(String.class);
        verify(outbox).append(eq("identity"), eq("ana"), eq("UserCreated"), identity.capture(), any());
        assertThat(identity.getValue()).contains("\"skipPasswordSetupEmail\" : true").doesNotContain("TFS01");
        verify(outbox).append(eq("employee-directory"), eq("employee-directory/ana"), eq("EmployeeUpserted"),
                contains("\"hotel\":\"TFS01\""), any());
        assertThat(user.popEvents()).isEmpty();
    }
}
