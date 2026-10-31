package io.mateu.ecdemo1.users.infra.in.ui.pages.users;

import io.mateu.ecdemo1.uicommons.crud.CatalogueEditor;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.EditableOnlyWhenCreating;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Lookup;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Style;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.ecdemo1.users.application.query.dto.UserDto;
import io.mateu.ecdemo1.users.application.usecases.user.create.CreateUserCommand;
import io.mateu.ecdemo1.users.application.usecases.user.create.CreateUserUseCase;
import io.mateu.ecdemo1.users.application.usecases.user.save.SaveUserCommand;
import io.mateu.ecdemo1.users.application.usecases.user.save.SaveUserUseCase;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeAttributes;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.RoleIdLabelSupplier;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.RoleIdOptionsSupplier;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.UserGroupIdLabelSupplier;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.UserGroupIdOptionsSupplier;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.attributes.CompanyValues;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.attributes.CountryValues;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.attributes.DepartmentValues;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.attributes.HotelValues;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.attributes.JobTitleValues;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.attributes.LanguageValues;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.attributes.RegionValues;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * A user's form. Besides who they are and what they may do (groups, roles), the employee attributes
 * the content service's audiences are written over — each a lookup on the attribute catalogue, so a
 * value is always one of its codes.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class UserViewModel implements CatalogueEditor<UserDto> {
    @Section("Usuario")
    @EditableOnlyWhenCreating
            @NotEmpty
    String id;
    @NotEmpty String name;
    String email;
    @Lookup(search = UserGroupIdOptionsSupplier.class, label = UserGroupIdLabelSupplier.class)
    List<String> groups;
    @Lookup(search = RoleIdOptionsSupplier.class, label = RoleIdLabelSupplier.class)
            @Colspan(2)
            @Style("width: 100%;")
    List<String> roles;

    @Section("Atributos de empleado")
    @Lookup(search = CompanyValues.class, label = CompanyValues.class)
    @Label("Empresa")
    String company;

    @Lookup(search = HotelValues.class, label = HotelValues.class)
    @Label("Hotel")
    String hotel;

    @Lookup(search = CountryValues.class, label = CountryValues.class)
    @Label("País")
    @Help("Con hotel, se toma el del hotel al guardar.")
    String country;

    @Lookup(search = RegionValues.class, label = RegionValues.class)
    @Label("Región")
    @Help("Con hotel, se toma la del hotel al guardar.")
    String region;

    @Lookup(search = DepartmentValues.class, label = DepartmentValues.class)
    @Label("Departamento")
    String department;

    @Lookup(search = JobTitleValues.class, label = JobTitleValues.class)
    @Label("Puesto")
    String jobTitle;

    @Lookup(search = LanguageValues.class, label = LanguageValues.class)
    @Label("Idioma")
    String language;

    final CreateUserUseCase createUserUseCase;
    final SaveUserUseCase saveUserUseCase;

    public String create(HttpRequest httpRequest) {
        createUserUseCase.handle(new CreateUserCommand(id, name, email, groups, roles, attributes()));
        return id;
    }

    public void save(HttpRequest httpRequest) {
        saveUserUseCase.handle(new SaveUserCommand(id, name, email, groups, roles, attributes()));
    }

    private EmployeeAttributes attributes() {
        return new EmployeeAttributes(company, country, region, hotel, department, jobTitle, language);
    }

    @Override
    public String id() {
        return id;
    }

    public UserViewModel load(UserDto user) {
        id = user.id();
        name = user.name();
        email = user.email();
        roles = user.roleIds();
        groups = user.groupIds();
        var attributes = user.attributes() == null ? EmployeeAttributes.NONE : user.attributes();
        company = attributes.company();
        country = attributes.country();
        region = attributes.region();
        hotel = attributes.hotel();
        department = attributes.department();
        jobTitle = attributes.jobTitle();
        language = attributes.language();
        return this;
    }

    @Override
    public String toString() {
        return id != null ? name : "New user";
    }
}
