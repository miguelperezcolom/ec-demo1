package io.mateu.ecdemo1.users.application.usecases.permission.save;

import io.mateu.ecdemo1.users.application.out.PermissionRepository;
import io.mateu.ecdemo1.users.domain.aggregates.permission.vo.PermissionId;
import io.mateu.ecdemo1.users.domain.aggregates.permission.vo.Scope;
import io.mateu.ecdemo1.users.domain.aggregates.shared.vo.Description;
import io.mateu.ecdemo1.users.domain.aggregates.shared.vo.Name;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SavePermissionUseCase {

    final PermissionRepository repository;

    @Transactional
    public void handle(SavePermissionCommand command) {
        var permission = repository.findById(new PermissionId(Long.valueOf(command.id()))).orElseThrow();
        permission.update(new Name(command.name()),
                new Description(command.description()),
                new Scope(command.scope()));
        repository.save(permission);
    }

}
