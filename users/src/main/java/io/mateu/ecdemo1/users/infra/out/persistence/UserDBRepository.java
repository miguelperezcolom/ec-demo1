package io.mateu.ecdemo1.users.infra.out.persistence;

import io.mateu.ecdemo1.users.application.out.UserRepository;
import io.mateu.ecdemo1.users.domain.aggregates.role.vo.RoleId;
import io.mateu.ecdemo1.users.domain.aggregates.shared.vo.Email;
import io.mateu.ecdemo1.users.domain.aggregates.shared.vo.Name;
import io.mateu.ecdemo1.users.domain.aggregates.shared.vo.Status;
import io.mateu.ecdemo1.users.domain.aggregates.user.User;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.UserId;
import io.mateu.ecdemo1.users.domain.aggregates.usergroup.vo.UserGroupId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import static io.mateu.core.infra.JsonSerializer.listFromJson;
import static io.mateu.core.infra.JsonSerializer.toJson;

@Service
@RequiredArgsConstructor
public class UserDBRepository implements UserRepository {

    final UserEntityRepository repository;

    @Override
    public Optional<User> findById(UserId id) {
        return repository.findById(id.id()).map(this::toDomain);
    }

    private User toDomain(UserEntity entity) {
        return new User(
                new UserId(entity.id),
                new Name(entity.name),
                new Email(entity.email),
                Status.valueOf(entity.status),
                listFromJson(entity.groupsJson, String.class).stream().map(UserGroupId::new).toList(),
                listFromJson(entity.rolesJson, String.class).stream().map(RoleId::new).toList()
        );
    }

    private UserEntity toEntity(User user) {
        return new UserEntity(
                user.getId().id(),
                user.getName().name(),
                user.getEmail().email(),
                toJson(user.getGroups().stream().map(UserGroupId::id).toList()),
                toJson(user.getRoles().stream().map(RoleId::id).toList()),
                user.getStatus().name()
        );
    }

    @Override
    public UserId save(User user) {
        return new UserId(repository.save(toEntity(user)).id);
    }


    @Override
    public void deleteAllById(List<UserId> selectedIds) {
        repository.deleteAllById(selectedIds.stream().map(UserId::id).toList());
    }
}
