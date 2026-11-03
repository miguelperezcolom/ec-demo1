package io.mateu.ecdemo1.users.infra.in.ui.pages.people;

import io.mateu.ecdemo1.uicommons.crud.CatalogueEditor;
import io.mateu.ecdemo1.users.application.query.dto.UserDto;
import io.mateu.ecdemo1.users.application.usecases.user.people.PersonAdministration;
import io.mateu.ecdemo1.users.application.usecases.user.people.PersonAdministration.Person;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeAttributes;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeProfile;
import io.mateu.ecdemo1.users.infra.in.security.PeopleAdmins;
import io.mateu.ecdemo1.users.infra.in.ui.Refusals;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.UserIdLabelSupplier;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.UserIdOptionsSupplier;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.attributes.CompanyValues;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.attributes.CountryValues;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.attributes.DepartmentValues;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.attributes.HotelValues;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.attributes.JobTitleValues;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.attributes.LanguageValues;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.attributes.RegionValues;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.EditableOnlyWhenCreating;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Lookup;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.interfaces.HttpRequest;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * An employee's form: who they are, where they work, who they report to. It has no roles or groups, and they
 * aren't accepted from the screen either: {@link PersonAdministration} keeps the ones they had.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@PeopleAdmins
public class PersonForm implements CatalogueEditor<UserDto> {

    @Section("Persona")
    @EditableOnlyWhenCreating
    @NotEmpty
    @Label("Usuario")
    String id;
    @NotEmpty
    @Label("Nombre")
    String name;
    @Label("Email")
    @Help("Si tiene permisos asignados, su email se cambia en Usuarios.")
    String email;

    @Section("Puesto")
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

    @Section("Directorio")
    @Lookup(search = UserIdOptionsSupplier.class, label = UserIdLabelSupplier.class)
    @Label("Responsable")
    @Help("A quién reporta: el organigrama de la intranet se dibuja con esto.")
    String managerId;
    @Label("Teléfono")
    String phone;

    @Section("Personas a cargo")
    @HiddenInCreate
    @Lookup(search = UserIdOptionsSupplier.class, label = UserIdLabelSupplier.class)
    @Label("Pasar todas a")
    @Help("Hazlo antes de dar de baja a un responsable: una baja con personas a cargo no se admite.")
    String newManagerOfReports;

    final PersonAdministration people;

    @Override
    public String create(HttpRequest httpRequest) {
        Refusals.shown(() -> people.create(person()));
        return id;
    }

    @Override
    public void save(HttpRequest httpRequest) {
        Refusals.shown(() -> people.save(person()));
    }

    /** A button of the edit form, by the field it uses: a toolbar's would be on the record's view, where the field is read-only. */
    @io.mateu.uidl.annotations.Button
    @Action
    @Label("Reasignar personas a cargo")
    public Object reasignar(HttpRequest httpRequest) {
        var moved = Refusals.answered(() -> people.reassignReports(id, newManagerOfReports));
        newManagerOfReports = null;
        return List.of(this, Message.success(moved == 0 ? "No tiene a nadie a cargo" : moved + " persona(s) reasignada(s)"));
    }

    private Person person() {
        return new Person(id, name, email, new EmployeeAttributes(company, country, region, hotel, department, jobTitle, language),
                new EmployeeProfile(managerId, phone));
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public PersonForm load(UserDto user) {
        id = user.id();
        name = user.name();
        email = user.email();
        var attributes = user.attributes() == null ? EmployeeAttributes.NONE : user.attributes();
        company = attributes.company();
        country = attributes.country();
        region = attributes.region();
        hotel = attributes.hotel();
        department = attributes.department();
        jobTitle = attributes.jobTitle();
        language = attributes.language();
        var profile = user.profile() == null ? EmployeeProfile.NONE : user.profile();
        managerId = profile.managerId();
        phone = profile.phone();
        return this;
    }

    @Override
    public String toString() {
        return id != null ? name : "Nueva persona";
    }
}
