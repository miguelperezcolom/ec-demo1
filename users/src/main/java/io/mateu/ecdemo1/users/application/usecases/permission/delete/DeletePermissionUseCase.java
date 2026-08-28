package io.mateu.ecdemo1.users.application.usecases.permission.delete;

import io.mateu.ecdemo1.users.application.out.PermissionRepository;
import io.mateu.ecdemo1.users.domain.aggregates.permission.vo.PermissionId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeletePermissionUseCase {

    final PermissionRepository repository;

    @Transactional
    public void handle(DeletePermissionCommand command) {
        repository.deleteAllById(command.ids().stream()
                .map(Long::valueOf)
                .map(PermissionId::new)
                .toList());
    }

}
