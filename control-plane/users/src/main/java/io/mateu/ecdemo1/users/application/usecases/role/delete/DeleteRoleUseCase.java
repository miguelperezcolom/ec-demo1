package io.mateu.ecdemo1.users.application.usecases.role.delete;

import io.mateu.ecdemo1.users.application.out.RoleRepository;
import io.mateu.ecdemo1.users.domain.aggregates.role.vo.RoleId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteRoleUseCase {

    final RoleRepository repository;

    @Transactional
    public void handle(DeleteRoleCommand command) {
        repository.deleteAllById(command.ids().stream()
                .map(RoleId::new)
                .toList());
    }

}
