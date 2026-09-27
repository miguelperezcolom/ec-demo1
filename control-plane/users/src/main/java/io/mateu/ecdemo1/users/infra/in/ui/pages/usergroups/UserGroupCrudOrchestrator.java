package io.mateu.ecdemo1.users.infra.in.ui.pages.usergroups;

import io.mateu.ecdemo1.uicommons.crud.CatalogueCrud;
import io.mateu.ecdemo1.users.application.query.UserGroupQueryService;
import io.mateu.ecdemo1.users.application.query.dto.UserGroupDto;
import io.mateu.ecdemo1.users.application.query.dto.UserGroupRow;
import io.mateu.ecdemo1.users.application.usecases.usergroup.delete.DeleteUserGroupCommand;
import io.mateu.ecdemo1.users.application.usecases.usergroup.delete.DeleteUserGroupUseCase;
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
@Title("User Groups")
public class UserGroupCrudOrchestrator extends CatalogueCrud<
        UserGroupViewModel, UserGroupViewModel, UserGroupViewModel, NoFilters, UserGroupRow, String, UserGroupDto> {

    final UserGroupViewModel viewModel;
    final DeleteUserGroupUseCase deleteUserGroupUseCase;
    final UserGroupQueryService queryService;

    @Override
    protected UserGroupViewModel editor() {
        return viewModel;
    }

    @Override
    protected UserGroupQueryService queries() {
        return queryService;
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        deleteUserGroupUseCase.handle(new DeleteUserGroupCommand(selectedIds));
    }
}
