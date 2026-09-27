package io.mateu.ecdemo1.users.infra.in.ui.pages.roles;

import io.mateu.ecdemo1.uicommons.crud.CatalogueCrud;
import io.mateu.ecdemo1.users.application.query.RoleQueryService;
import io.mateu.ecdemo1.users.application.query.dto.RoleDto;
import io.mateu.ecdemo1.users.application.query.dto.RoleRow;
import io.mateu.ecdemo1.users.application.usecases.role.delete.DeleteRoleCommand;
import io.mateu.ecdemo1.users.application.usecases.role.delete.DeleteRoleUseCase;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.NoFilters;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

/** The CRUD of this catalogue: listing, form and queries in {@link CatalogueCrud}; deleting here. */
@Service
@RequiredArgsConstructor
@Scope("prototype")
@Title("Roles")
public class RolesCrudOrchestrator extends CatalogueCrud<
        RoleViewModel, RoleViewModel, RoleViewModel, NoFilters, RoleRow, String, RoleDto> {

    final RoleViewModel viewModel;
    final DeleteRoleUseCase deleteRoleUseCase;
    final RoleQueryService queryService;

    @Override
    protected RoleViewModel editor() {
        return viewModel;
    }

    @Override
    protected RoleQueryService queries() {
        return queryService;
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        deleteRoleUseCase.handle(new DeleteRoleCommand(selectedIds));
    }
}
