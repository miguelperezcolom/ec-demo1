package io.mateu.ecdemo1.users.application.usecases.user.people;

import io.mateu.ecdemo1.users.application.out.UserRepository;
import io.mateu.ecdemo1.users.application.query.UserQueryService;
import io.mateu.ecdemo1.users.application.query.dto.UserDto;
import io.mateu.ecdemo1.users.application.usecases.user.create.CreateUserCommand;
import io.mateu.ecdemo1.users.application.usecases.user.create.CreateUserUseCase;
import io.mateu.ecdemo1.users.application.usecases.user.delete.DeleteUserCommand;
import io.mateu.ecdemo1.users.application.usecases.user.delete.DeleteUserUseCase;
import io.mateu.ecdemo1.users.application.usecases.user.people.PersonAdministration.Person;
import io.mateu.ecdemo1.users.application.usecases.user.save.SaveUserCommand;
import io.mateu.ecdemo1.users.application.usecases.user.save.SaveUserUseCase;
import io.mateu.ecdemo1.users.domain.aggregates.user.User;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeAttributes;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeProfile;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonAdministrationTest {

    final UserRepository repository = mock(UserRepository.class);
    final UserQueryService users = mock(UserQueryService.class);
    final CreateUserUseCase create = mock(CreateUserUseCase.class);
    final SaveUserUseCase save = mock(SaveUserUseCase.class);
    final DeleteUserUseCase delete = mock(DeleteUserUseCase.class);
    final ReportingLine line = new ReportingLine(repository);
    final PersonAdministration people = new PersonAdministration(users, create, save, delete, line);

    final Map<String, User> stored = new HashMap<>();

    @BeforeEach
    void theRepository() {
        when(repository.findAll()).thenAnswer(i -> new ArrayList<>(stored.values()));
        when(repository.findById(any())).thenAnswer(i -> Optional.ofNullable(stored.get(((UserId) i.getArgument(0)).id())));
    }

    void employee(String id, String manager) {
        var user = mock(User.class);
        when(user.getId()).thenReturn(new UserId(id));
        when(user.getName()).thenReturn(new io.mateu.ecdemo1.users.domain.aggregates.shared.vo.Name(id));
        when(user.getProfile()).thenReturn(new EmployeeProfile(manager, null));
        stored.put(id, user);
        when(users.getById(id)).thenReturn(Optional.of(dto(id, manager, List.of())));
    }

    static UserDto dto(String id, String manager, List<String> roles) {
        return new UserDto(id, id, id + "@example.com", List.of(), roles, EmployeeAttributes.NONE, new EmployeeProfile(manager, null));
    }

    static Person person(String id, String email, String manager) {
        return new Person(id, id, email, EmployeeAttributes.NONE, new EmployeeProfile(manager, null));
    }

    @Test
    void nobody_is_their_own_responsible_nor_below_themselves() {
        employee("a", null);
        employee("b", "a");
        employee("c", "b");

        assertThatThrownBy(() -> line.check("a", new EmployeeProfile("a", null))).hasMessageContaining("propio");
        assertThatThrownBy(() -> line.check("a", new EmployeeProfile("c", null))).hasMessageContaining("bucle");
        line.check("c", new EmployeeProfile("a", null));
    }

    @Test
    void a_responsible_with_people_left_behind_is_refused_unless_they_leave_too() {
        employee("boss", null);
        employee("report", "boss");

        assertThatThrownBy(() -> line.checkNobodyLeftBehind(Set.of("boss"))).hasMessageContaining("personas a cargo");
        line.checkNobodyLeftBehind(Set.of("boss", "report"));
        line.checkNobodyLeftBehind(Set.of("report"));
    }

    @Test
    void creating_gives_no_access() {
        people.create(person("new", "n@example.com", null));

        var sent = ArgumentCaptor.forClass(CreateUserCommand.class);
        verify(create).handle(sent.capture());
        assertThat(sent.getValue().roleIds()).isEmpty();
        assertThat(sent.getValue().groupIds()).isEmpty();
    }

    @Test
    void a_responsible_who_does_not_exist_is_refused() {
        when(users.getById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> people.create(person("new", "n@example.com", "ghost"))).hasMessageContaining("No existe");
    }

    @Test
    void saving_keeps_the_roles_and_groups_they_had() {
        when(users.getById("eva")).thenReturn(Optional.of(new UserDto("eva", "Eva", "eva@example.com", List.of("g"), List.of("content-admin"),
                EmployeeAttributes.NONE, EmployeeProfile.NONE)));

        people.save(person("eva", "eva@example.com", null));

        var sent = ArgumentCaptor.forClass(SaveUserCommand.class);
        verify(save).handle(sent.capture());
        assertThat(sent.getValue().roles()).containsExactly("content-admin");
        assertThat(sent.getValue().groups()).containsExactly("g");
    }

    @Test
    void the_email_of_who_holds_access_is_not_changed_here_or_a_reset_would_take_their_account() {
        when(users.getById("admin")).thenReturn(Optional.of(dto("admin", null, List.of("ai-admin"))));

        assertThatThrownBy(() -> people.save(person("admin", "mine@example.com", null))).hasMessageContaining("Usuarios");
        verify(save, never()).handle(any());
    }

    @Test
    void who_holds_access_is_not_removed_here() {
        when(users.getById("admin")).thenReturn(Optional.of(dto("admin", null, List.of("ai-admin"))));

        assertThatThrownBy(() -> people.remove(List.of("admin"))).hasMessageContaining("Usuarios");
        verify(delete, never()).handle(any(DeleteUserCommand.class));
    }

    @Test
    void reports_move_to_the_new_responsible_keeping_the_rest() {
        employee("boss", null);
        employee("report", "boss");

        assertThat(people.reassignReports("boss", "other")).isEqualTo(1);

        var sent = ArgumentCaptor.forClass(SaveUserCommand.class);
        verify(save).handle(sent.capture());
        assertThat(sent.getValue().id()).isEqualTo("report");
        assertThat(sent.getValue().profile().managerId()).isEqualTo("other");
        assertThatThrownBy(() -> people.reassignReports("boss", " ")).hasMessageContaining("nuevo responsable");
    }
}
