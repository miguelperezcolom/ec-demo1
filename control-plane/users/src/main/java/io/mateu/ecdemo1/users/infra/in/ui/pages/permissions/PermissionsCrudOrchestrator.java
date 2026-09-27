package io.mateu.ecdemo1.users.infra.in.ui.pages.permissions;

import io.mateu.ecdemo1.uicommons.crud.CatalogueCrud;
import io.mateu.ecdemo1.users.application.query.PermissionQueryService;
import io.mateu.ecdemo1.users.application.query.dto.PermissionDto;
import io.mateu.ecdemo1.users.application.query.dto.PermissionRow;
import io.mateu.ecdemo1.users.application.usecases.permission.delete.DeletePermissionCommand;
import io.mateu.ecdemo1.users.application.usecases.permission.delete.DeletePermissionUseCase;
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
@Title("Permissions")
public class PermissionsCrudOrchestrator extends CatalogueCrud<
        PermissionViewModel, PermissionViewModel, PermissionViewModel, NoFilters, PermissionRow, String, PermissionDto> {

    final PermissionViewModel viewModel;
    final DeletePermissionUseCase deletePermissionUseCase;
    final PermissionQueryService queryService;

    @Override
    protected PermissionViewModel editor() {
        return viewModel;
    }

    @Override
    protected PermissionQueryService queries() {
        return queryService;
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        deletePermissionUseCase.handle(new DeletePermissionCommand(selectedIds));
    }
}
