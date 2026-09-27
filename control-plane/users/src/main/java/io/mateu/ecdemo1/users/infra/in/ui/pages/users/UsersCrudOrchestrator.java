package io.mateu.ecdemo1.users.infra.in.ui.pages.users;

import io.mateu.ecdemo1.uicommons.crud.CatalogueCrud;
import io.mateu.ecdemo1.users.application.query.UserQueryService;
import io.mateu.ecdemo1.users.application.query.dto.UserDto;
import io.mateu.ecdemo1.users.application.query.dto.UserRow;
import io.mateu.ecdemo1.users.application.usecases.user.delete.DeleteUserCommand;
import io.mateu.ecdemo1.users.application.usecases.user.delete.DeleteUserUseCase;
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
@Title("Users")
public class UsersCrudOrchestrator extends CatalogueCrud<
        UserViewModel, UserViewModel, UserViewModel, NoFilters, UserRow, String, UserDto> {

    final UserViewModel viewModel;
    final DeleteUserUseCase deleteUserUseCase;
    final UserQueryService queryService;

    @Override
    protected UserViewModel editor() {
        return viewModel;
    }

    @Override
    protected UserQueryService queries() {
        return queryService;
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        deleteUserUseCase.handle(new DeleteUserCommand(selectedIds));
    }
}
