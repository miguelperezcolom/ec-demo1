package io.mateu.ecdemo1.users.application.usecases.user.people;

import io.mateu.ecdemo1.users.application.query.UserQueryService;
import io.mateu.ecdemo1.users.application.query.dto.UserDto;
import io.mateu.ecdemo1.users.application.usecases.user.create.CreateUserCommand;
import io.mateu.ecdemo1.users.application.usecases.user.create.CreateUserUseCase;
import io.mateu.ecdemo1.users.application.usecases.user.delete.DeleteUserCommand;
import io.mateu.ecdemo1.users.application.usecases.user.delete.DeleteUserUseCase;
import io.mateu.ecdemo1.users.application.usecases.user.save.SaveUserCommand;
import io.mateu.ecdemo1.users.application.usecases.user.save.SaveUserUseCase;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeAttributes;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Employees as human resources keep them: who they are, where they work, who they report to. Never what they
 * may do. Roles and groups aren't taken from the caller, and whoever holds any is out of reach for what would
 * take over their account: changing their email (a password reset goes there) or removing them. Those stay in
 * Usuarios, on the control console, or a people:edit could make itself an administrator.
 */
@Service
@RequiredArgsConstructor
public class PersonAdministration {

    public record Person(String id, String name, String email, EmployeeAttributes attributes, EmployeeProfile profile) {
    }

    final UserQueryService users;
    final CreateUserUseCase create;
    final SaveUserUseCase save;
    final DeleteUserUseCase delete;
    final ReportingLine line;

    @Transactional
    public void create(Person person) {
        requireResponsible(person);
        create.handle(new CreateUserCommand(person.id(), person.name(), person.email(), List.of(), List.of(),
                person.attributes(), person.profile(), false));
    }

    @Transactional
    public void save(Person person) {
        requireResponsible(person);
        var current = current(person.id());
        if (holdsAccess(current) && !Objects.equals(blankToNull(current.email()), blankToNull(person.email()))) {
            throw new IllegalArgumentException(person.name() + " tiene permisos asignados: su email se cambia en Usuarios");
        }
        save.handle(new SaveUserCommand(person.id(), person.name(), person.email(), current.groupIds(), current.roleIds(),
                person.attributes(), person.profile()));
    }

    @Transactional
    public void remove(List<String> ids) {
        for (var id : ids) {
            var current = current(id);
            if (holdsAccess(current)) {
                throw new IllegalArgumentException(current.name() + " tiene permisos asignados: su baja se da en Usuarios");
            }
        }
        delete.handle(new DeleteUserCommand(ids));
    }

    /** Everyone who reports to {@code from} now reports to {@code to}. How many they were. */
    @Transactional
    public int reassignReports(String from, String to) {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Elige el nuevo responsable");
        }
        var reports = line.reportsTo(from);
        for (var report : reports) {
            var current = current(report.getId().id());
            var profile = current.profile() == null ? EmployeeProfile.NONE : current.profile();
            save.handle(new SaveUserCommand(current.id(), current.name(), current.email(), current.groupIds(),
                    current.roleIds(), current.attributes(), new EmployeeProfile(to, profile.phone())));
        }
        return reports.size();
    }

    private void requireResponsible(Person person) {
        var manager = person.profile() == null ? null : person.profile().managerId();
        if (manager != null && users.getById(manager).isEmpty()) {
            throw new IllegalArgumentException("No existe el responsable " + manager);
        }
    }

    private UserDto current(String id) {
        return users.getById(id).orElseThrow(() -> new IllegalArgumentException("No existe la persona " + id));
    }

    private static boolean holdsAccess(UserDto user) {
        return (user.roleIds() != null && !user.roleIds().isEmpty()) || (user.groupIds() != null && !user.groupIds().isEmpty());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
