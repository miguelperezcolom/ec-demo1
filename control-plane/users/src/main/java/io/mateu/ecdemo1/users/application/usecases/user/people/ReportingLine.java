package io.mateu.ecdemo1.users.application.usecases.user.people;

import io.mateu.ecdemo1.users.application.out.UserRepository;
import io.mateu.ecdemo1.users.domain.aggregates.user.User;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeProfile;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Who reports to whom: the org chart is a tree, so nobody is above themselves. */
@Service
@RequiredArgsConstructor
public class ReportingLine {

    final UserRepository repository;

    /** Refuses a responsible who would make a loop. Not whether they exist: a seed creates people in any order. */
    public void check(String id, EmployeeProfile profile) {
        var manager = profile == null ? null : profile.managerId();
        var seen = new HashSet<String>();
        for (var current = manager; current != null && seen.add(current); current = managerOf(current)) {
            if (current.equals(id)) {
                throw new IllegalArgumentException(manager.equals(id)
                        ? "Nadie puede ser su propio responsable"
                        : "Ese responsable ya depende de esta persona: el organigrama haría un bucle");
            }
        }
    }

    /** Who reports directly to {@code id}. */
    public List<User> reportsTo(String id) {
        return repository.findAll().stream()
                .filter(u -> u.getProfile() != null && id.equals(u.getProfile().managerId()))
                .toList();
    }

    /** Refuses if someone outside {@code leaving} still reports to one of them: they'd be left with nobody. */
    public void checkNobodyLeftBehind(Set<String> leaving) {
        var behind = repository.findAll().stream()
                .filter(u -> u.getProfile() != null && u.getProfile().managerId() != null)
                .filter(u -> leaving.contains(u.getProfile().managerId()))
                .filter(u -> !leaving.contains(u.getId().id()))
                .map(u -> u.getName().name())
                .sorted().toList();
        if (!behind.isEmpty()) {
            throw new IllegalArgumentException("Tiene personas a cargo (" + String.join(", ", behind)
                    + "): asígnales otro responsable antes de la baja");
        }
    }

    private String managerOf(String id) {
        return repository.findById(new UserId(id)).map(User::getProfile).map(EmployeeProfile::managerId).orElse(null);
    }
}
