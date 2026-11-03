package io.mateu.ecdemo1.users.infra.in.ui.pages.people;

import io.mateu.ecdemo1.uicommons.crud.CatalogueCrud;
import io.mateu.ecdemo1.users.application.query.UserQueryService;
import io.mateu.ecdemo1.users.application.query.dto.UserDto;
import io.mateu.ecdemo1.users.application.query.dto.UserRow;
import io.mateu.ecdemo1.users.application.usecases.user.people.PersonAdministration;
import io.mateu.ecdemo1.users.infra.in.security.PeopleAdmins;
import io.mateu.ecdemo1.users.infra.in.ui.Refusals;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.NoFilters;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Scope("prototype")
@Title("Empleados")
@PeopleAdmins
public class PeopleCrud extends CatalogueCrud<PersonForm, PersonForm, PersonForm, NoFilters, UserRow, String, UserDto> {

    final PersonForm form;
    final PersonAdministration people;
    final UserQueryService queryService;

    @Override
    protected PersonForm editor() {
        return form;
    }

    @Override
    protected UserQueryService queries() {
        return queryService;
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        Refusals.shown(() -> people.remove(selectedIds));
    }
}
