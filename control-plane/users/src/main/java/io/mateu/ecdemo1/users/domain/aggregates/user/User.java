package io.mateu.ecdemo1.users.domain.aggregates.user;


import io.mateu.workflow.ddd.AggregateRoot;
import io.mateu.ecdemo1.users.domain.aggregates.role.vo.RoleId;
import io.mateu.ecdemo1.users.domain.aggregates.shared.vo.Email;
import io.mateu.ecdemo1.users.domain.aggregates.shared.vo.Name;
import io.mateu.ecdemo1.users.domain.aggregates.shared.vo.Status;
import io.mateu.ecdemo1.users.domain.aggregates.user.events.UserCreated;
import io.mateu.ecdemo1.users.domain.aggregates.user.events.UserDeleted;
import io.mateu.ecdemo1.users.domain.aggregates.user.events.UserUpdated;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeAttributes;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.UserId;
import io.mateu.ecdemo1.users.domain.aggregates.usergroup.vo.UserGroupId;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor@NoArgsConstructor
@Getter
public class User extends AggregateRoot {

    UserId id;

    Name name;

    Email email;

    Status status;

    List<UserGroupId> groups;

    List<RoleId> roles;

    /**
     * The employee attributes the content service's audiences are written over. Never null: a user
     * without any is {@link EmployeeAttributes#NONE}. Who derives country and region from the hotel
     * is the caller's business (it needs the catalogue); the aggregate keeps what it is given.
     */
    EmployeeAttributes attributes;

    public static User of(UserId userId, Name name, Email email, List<UserGroupId> groups, List<RoleId> roles,
                          EmployeeAttributes attributes) {
        var user = new User(userId, name, email, Status.Active, groups, roles, orNone(attributes));
        user.send(new UserCreated(userId.id(), name.name(), email.email(), user.isEnabled(), user.attributes));
        return user;
    }

    public void update(Name name, Email email, List<UserGroupId> groups, List<RoleId> roles,
                       EmployeeAttributes attributes) {
        this.name = name;
        this.email = email;
        this.groups = groups;
        this.roles = roles;
        this.attributes = orNone(attributes);
        send(new UserUpdated(id.id(), name.name(), email.email(), isEnabled(), this.attributes));
    }

    private static EmployeeAttributes orNone(EmployeeAttributes attributes) {
        return attributes == null ? EmployeeAttributes.NONE : attributes;
    }

    /**
     * Whether this user may sign in, as a subscriber outside the domain needs to hear it. Active is
     * the one status that means yes; disabled and archived both mean no, and the difference between
     * those two is this service's business, not the identity provider's.
     */
    public boolean isEnabled() {
        return status == Status.Active;
    }

    /**
     * Record that this user is being removed. Raises {@link UserDeleted} so the same removal
     * reaches the identity provider — a user gone from here but left there could still sign in.
     * Called on a loaded aggregate just before it is deleted, so the event is drained into the same
     * transaction that removes the row.
     */
    public void markDeleted() {
        send(new UserDeleted(id.id()));
    }

}
