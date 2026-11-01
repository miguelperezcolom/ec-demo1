package io.mateu.ecdemo1.users.domain.aggregates.user;

import io.mateu.ecdemo1.users.domain.aggregates.shared.vo.Email;
import io.mateu.ecdemo1.users.domain.aggregates.shared.vo.Name;
import io.mateu.ecdemo1.users.domain.aggregates.user.events.UserUpdated;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeAttributes;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeProfile;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.UserId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A user's directory profile: who they report to and their phone. */
class UserProfileTest {

    static User ana(EmployeeProfile profile) {
        return User.of(new UserId("ana"), new Name("Ana Pérez"), new Email("ana@example.com"), List.of(), List.of(),
                EmployeeAttributes.NONE, profile);
    }

    @Test
    void theProfileIsKeptAndTravelsInTheEvent() {
        var user = ana(new EmployeeProfile("luis", " +34 600 000 101 "));
        user.update(user.getName(), user.getEmail(), List.of(), List.of(), EmployeeAttributes.NONE,
                new EmployeeProfile("marta", ""));

        assertThat(user.getProfile()).isEqualTo(new EmployeeProfile("marta", null));
        assertThat(user.popEvents()).last().isInstanceOfSatisfying(UserUpdated.class,
                e -> assertThat(e.profile().managerId()).isEqualTo("marta"));
    }

    @Test
    void anUpdateWithoutAProfileKeepsTheOneTheUserHas() {
        var user = ana(new EmployeeProfile("luis", "+34 600 000 101"));
        user.update(user.getName(), user.getEmail(), List.of(), List.of(), EmployeeAttributes.NONE);

        assertThat(user.getProfile().managerId()).isEqualTo("luis");
    }

    @Test
    void nobodyReportsToThemselves() {
        assertThatThrownBy(() -> ana(new EmployeeProfile("ana", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
