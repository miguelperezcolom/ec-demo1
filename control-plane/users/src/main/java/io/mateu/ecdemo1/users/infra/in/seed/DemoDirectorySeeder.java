package io.mateu.ecdemo1.users.infra.in.seed;

import io.mateu.ecdemo1.users.application.out.AttributeValueRepository;
import io.mateu.ecdemo1.users.application.out.PermissionRepository;
import io.mateu.ecdemo1.users.application.out.RoleRepository;
import io.mateu.ecdemo1.users.application.query.UserQueryService;
import io.mateu.ecdemo1.users.application.usecases.attributevalue.create.CreateAttributeValueCommand;
import io.mateu.ecdemo1.users.application.usecases.attributevalue.create.CreateAttributeValueUseCase;
import io.mateu.ecdemo1.users.application.usecases.permission.create.CreatePermissionCommand;
import io.mateu.ecdemo1.users.application.usecases.permission.create.CreatePermissionUseCase;
import io.mateu.ecdemo1.users.application.usecases.role.create.CreateRoleCommand;
import io.mateu.ecdemo1.users.application.usecases.role.create.CreateRoleUseCase;
import io.mateu.ecdemo1.users.application.usecases.user.create.CreateUserCommand;
import io.mateu.ecdemo1.users.application.usecases.user.create.CreateUserUseCase;
import io.mateu.ecdemo1.users.application.usecases.user.save.SaveUserCommand;
import io.mateu.ecdemo1.users.application.usecases.user.save.SaveUserUseCase;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.Attribute;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.AttributeValueId;
import io.mateu.ecdemo1.users.domain.aggregates.permission.vo.Scope;
import io.mateu.ecdemo1.users.domain.aggregates.role.vo.RoleId;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeAttributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.Attribute.COMPANY;
import static io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.Attribute.COUNTRY;
import static io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.Attribute.DEPARTMENT;
import static io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.Attribute.HOTEL;
import static io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.Attribute.JOB_TITLE;
import static io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.Attribute.LANGUAGE;
import static io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.Attribute.REGION;

/**
 * The content demo's data in the users service: the attribute catalogue, the editorial permissions and
 * roles, and some thirty employees spread over the demo's hotels, departments, job titles and
 * languages — enough for "view as" to show different profiles seeing different things.
 *
 * <p>Idempotent, and additive only: at every start it creates what is missing and leaves what is there
 * alone — a label someone edited, a user someone moved to another hotel. Deleting a seeded entry and
 * restarting brings it back; turn the seed off ({@code users.demo-seed.enabled=false}) where that is
 * not wanted.
 *
 * <p>Users go through the ordinary create use case, so they reach Keycloak and the employee directory
 * like any other — but without the set-password email: their addresses are at example.com. They are
 * created with no password, so they cannot sign in until someone sets one in Keycloak.
 *
 * <p>Each entry is created on its own and a failure is logged and skipped: the service must start
 * whatever the seed finds, and a later start fills in what this one could not.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DemoDirectorySeeder {

    static final String HOTELS_COMPANY = "RIUDEMO_HOTELES";
    static final String CENTRAL_COMPANY = "RIUDEMO_CENTRAL";

    record CatalogueEntry(Attribute attribute, String code, String label, String country, String region) {
        static CatalogueEntry of(Attribute attribute, String code, String label) {
            return new CatalogueEntry(attribute, code, label, null, null);
        }
    }

    record PermissionSeed(String name, String description, String scope) {
    }

    record RoleSeed(String id, String name, String description, List<String> scopes) {
    }

    /** An employee; hotel staff are in the hotels company, the rest in the central one. */
    record Employee(String id, String name, String hotel, String department, String jobTitle, String language,
                    List<String> roles) {

        static Employee at(String hotel, String id, String name, String department, String jobTitle,
                           String language) {
            return new Employee(id, name, hotel, department, jobTitle, language, List.of());
        }

        /** Country and region left out where there is a hotel: the create use case derives them. */
        EmployeeAttributes attributes() {
            return hotel != null
                    ? new EmployeeAttributes(HOTELS_COMPANY, null, null, hotel, department, jobTitle, language)
                    : new EmployeeAttributes(CENTRAL_COMPANY, "ES", "BALEARES", null, department, jobTitle, language);
        }
    }

    static final List<CatalogueEntry> CATALOGUE = List.of(
            CatalogueEntry.of(COMPANY, HOTELS_COMPANY, "Riu Demo Hoteles"),
            CatalogueEntry.of(COMPANY, CENTRAL_COMPANY, "Riu Demo Servicios Centrales"),

            CatalogueEntry.of(COUNTRY, "ES", "España"),
            CatalogueEntry.of(COUNTRY, "MU", "Mauricio"),
            CatalogueEntry.of(COUNTRY, "MX", "México"),

            CatalogueEntry.of(REGION, "CANARIAS", "Canarias"),
            CatalogueEntry.of(REGION, "BALEARES", "Baleares"),
            CatalogueEntry.of(REGION, "MAURICIO", "Mauricio"),
            CatalogueEntry.of(REGION, "QUINTANA_ROO", "Quintana Roo"),

            // MRU01, PMI01 and CUN01 are the demo's hotels (front office, CRS, registration rules);
            // TFS01 and LPA01 exist only here, so there is a Canarias to write an audience for.
            new CatalogueEntry(HOTEL, "MRU01", "Riu Demo Mauricio", "MU", "MAURICIO"),
            new CatalogueEntry(HOTEL, "PMI01", "Riu Demo Palma", "ES", "BALEARES"),
            new CatalogueEntry(HOTEL, "CUN01", "Riu Demo Cancún", "MX", "QUINTANA_ROO"),
            new CatalogueEntry(HOTEL, "TFS01", "Riu Demo Tenerife", "ES", "CANARIAS"),
            new CatalogueEntry(HOTEL, "LPA01", "Riu Demo Gran Canaria", "ES", "CANARIAS"),

            CatalogueEntry.of(DEPARTMENT, "RECEPCION", "Recepción"),
            CatalogueEntry.of(DEPARTMENT, "PISOS", "Pisos"),
            CatalogueEntry.of(DEPARTMENT, "COCINA", "Cocina"),
            CatalogueEntry.of(DEPARTMENT, "MANTENIMIENTO", "Mantenimiento"),
            CatalogueEntry.of(DEPARTMENT, "COMERCIAL", "Comercial"),
            CatalogueEntry.of(DEPARTMENT, "DIRECCION", "Dirección"),
            CatalogueEntry.of(DEPARTMENT, "RRHH", "RRHH"),
            CatalogueEntry.of(DEPARTMENT, "IT", "IT"),

            CatalogueEntry.of(JOB_TITLE, "RECEPCIONISTA", "Recepcionista"),
            CatalogueEntry.of(JOB_TITLE, "JEFE_RECEPCION", "Jefe de recepción"),
            CatalogueEntry.of(JOB_TITLE, "GOBERNANTA", "Gobernanta"),
            CatalogueEntry.of(JOB_TITLE, "CAMARERA_PISOS", "Camarera de pisos"),
            CatalogueEntry.of(JOB_TITLE, "CHEF", "Chef"),
            CatalogueEntry.of(JOB_TITLE, "DIRECTOR_HOTEL", "Director de hotel"),
            CatalogueEntry.of(JOB_TITLE, "TECNICO_MANTENIMIENTO", "Técnico de mantenimiento"),
            CatalogueEntry.of(JOB_TITLE, "COMERCIAL", "Comercial"),
            CatalogueEntry.of(JOB_TITLE, "TECNICO_RRHH", "Técnico RRHH"),

            CatalogueEntry.of(LANGUAGE, "es", "Español"),
            CatalogueEntry.of(LANGUAGE, "en", "English"),
            CatalogueEntry.of(LANGUAGE, "de", "Deutsch")
    );

    static final List<PermissionSeed> PERMISSIONS = List.of(
            new PermissionSeed("Editar contenido", "Crear y editar contenido de la intranet", "content:edit"),
            new PermissionSeed("Publicar contenido", "Publicar y retirar contenido de la intranet", "content:publish"),
            new PermissionSeed("Administrar contenido", "Audiencias, navegación, slots y newsletter", "content:admin")
    );

    static final String CONTENT_ADMIN = "content-admin";

    static final List<RoleSeed> ROLES = List.of(
            new RoleSeed("content-editor", "Editor de contenido", "Redacta contenido de la intranet",
                    List.of("content:edit")),
            new RoleSeed("content-publisher", "Publicador de contenido", "Redacta y publica contenido de la intranet",
                    List.of("content:edit", "content:publish")),
            new RoleSeed(CONTENT_ADMIN, "Administrador de contenido", "Todo el contenido de la intranet",
                    List.of("content:edit", "content:publish", "content:admin"))
    );

    static final List<Employee> EMPLOYEES = List.of(
            // Tenerife — Canarias
            Employee.at("TFS01", "ana.perez", "Ana Pérez", "RECEPCION", "RECEPCIONISTA", "es"),
            Employee.at("TFS01", "carlos.diaz", "Carlos Díaz", "RECEPCION", "JEFE_RECEPCION", "es"),
            Employee.at("TFS01", "lucia.hernandez", "Lucía Hernández", "PISOS", "GOBERNANTA", "es"),
            Employee.at("TFS01", "maria.santana", "María Santana", "PISOS", "CAMARERA_PISOS", "es"),
            Employee.at("TFS01", "jonas.weber", "Jonas Weber", "COCINA", "CHEF", "de"),
            Employee.at("TFS01", "elena.rodriguez", "Elena Rodríguez", "DIRECCION", "DIRECTOR_HOTEL", "es"),
            Employee.at("TFS01", "pedro.gonzalez", "Pedro González", "MANTENIMIENTO", "TECNICO_MANTENIMIENTO", "es"),
            // Gran Canaria — Canarias
            Employee.at("LPA01", "laura.suarez", "Laura Suárez", "RECEPCION", "RECEPCIONISTA", "es"),
            Employee.at("LPA01", "sofia.mueller", "Sofia Müller", "RECEPCION", "RECEPCIONISTA", "de"),
            Employee.at("LPA01", "david.ramos", "David Ramos", "DIRECCION", "DIRECTOR_HOTEL", "es"),
            Employee.at("LPA01", "carmen.vega", "Carmen Vega", "PISOS", "CAMARERA_PISOS", "es"),
            Employee.at("LPA01", "tom.baker", "Tom Baker", "COMERCIAL", "COMERCIAL", "en"),
            // Palma — Baleares
            Employee.at("PMI01", "marta.ferrer", "Marta Ferrer", "RECEPCION", "JEFE_RECEPCION", "es"),
            Employee.at("PMI01", "joan.mas", "Joan Mas", "RECEPCION", "RECEPCIONISTA", "es"),
            Employee.at("PMI01", "klaus.schmidt", "Klaus Schmidt", "RECEPCION", "RECEPCIONISTA", "de"),
            Employee.at("PMI01", "francisca.roig", "Francisca Roig", "PISOS", "GOBERNANTA", "es"),
            Employee.at("PMI01", "miquel.serra", "Miquel Serra", "COCINA", "CHEF", "es"),
            Employee.at("PMI01", "antonia.vidal", "Antonia Vidal", "DIRECCION", "DIRECTOR_HOTEL", "es"),
            Employee.at("PMI01", "hannah.fischer", "Hannah Fischer", "COMERCIAL", "COMERCIAL", "de"),
            // Mauricio
            Employee.at("MRU01", "priya.ramgoolam", "Priya Ramgoolam", "RECEPCION", "RECEPCIONISTA", "en"),
            Employee.at("MRU01", "jean.laval", "Jean Laval", "RECEPCION", "JEFE_RECEPCION", "en"),
            Employee.at("MRU01", "anisha.doorgakant", "Anisha Doorgakant", "PISOS", "CAMARERA_PISOS", "en"),
            Employee.at("MRU01", "kevin.lee", "Kevin Lee", "COCINA", "CHEF", "en"),
            Employee.at("MRU01", "raj.patel", "Raj Patel", "DIRECCION", "DIRECTOR_HOTEL", "en"),
            Employee.at("MRU01", "nicolas.duval", "Nicolas Duval", "MANTENIMIENTO", "TECNICO_MANTENIMIENTO", "en"),
            // Cancún — Quintana Roo
            Employee.at("CUN01", "alejandra.lopez", "Alejandra López", "RECEPCION", "RECEPCIONISTA", "es"),
            Employee.at("CUN01", "jorge.martinez", "Jorge Martínez", "RECEPCION", "JEFE_RECEPCION", "es"),
            Employee.at("CUN01", "gabriela.cruz", "Gabriela Cruz", "PISOS", "GOBERNANTA", "es"),
            Employee.at("CUN01", "emily.johnson", "Emily Johnson", "COMERCIAL", "COMERCIAL", "en"),
            Employee.at("CUN01", "luis.hernandez", "Luis Hernández", "MANTENIMIENTO", "TECNICO_MANTENIMIENTO", "es"),
            // Servicios centrales (Palma), no hotel
            Employee.at(null, "isabel.torres", "Isabel Torres", "RRHH", "TECNICO_RRHH", "es"),
            Employee.at(null, "miguel.navarro", "Miguel Navarro", "IT", null, "es"),
            // The editorial team the content demo signs in as
            new Employee("editora", "Eva Morales", null, "RRHH", "TECNICO_RRHH", "es", List.of("content-editor")),
            new Employee("publicador", "Pablo Ortega", null, "DIRECCION", null, "es", List.of("content-publisher"))
    );

    /** The realm's demo user: headquarters, management, Spanish — and content administrator. */
    static final String DEMO_USER = "demo";
    static final EmployeeAttributes DEMO_ATTRIBUTES =
            new EmployeeAttributes(CENTRAL_COMPANY, "ES", "BALEARES", null, "DIRECCION", null, "es");

    final AttributeValueRepository attributeValues;
    final PermissionRepository permissions;
    final RoleRepository roles;
    final UserQueryService users;
    final CreateAttributeValueUseCase createAttributeValue;
    final CreatePermissionUseCase createPermission;
    final CreateRoleUseCase createRole;
    final CreateUserUseCase createUser;
    final SaveUserUseCase saveUser;

    @Value("${users.demo-seed.enabled:true}")
    boolean enabled;

    @EventListener(ApplicationReadyEvent.class)
    public void atStartup() {
        if (!enabled) {
            log.info("Demo seed off (users.demo-seed.enabled=false)");
            return;
        }
        seed();
    }

    public void seed() {
        int created = 0;
        for (var value : CATALOGUE) {
            created += attempt("attribute value " + value.attribute() + ":" + value.code(), () -> {
                if (attributeValues.findById(AttributeValueId.of(value.attribute(), value.code())).isPresent()) {
                    return false;
                }
                createAttributeValue.handle(new CreateAttributeValueCommand(value.attribute().name(), value.code(),
                        value.label(), value.country(), value.region()));
                return true;
            });
        }
        for (var permission : PERMISSIONS) {
            created += attempt("permission " + permission.scope(), () -> {
                if (permissions.findIdByScope(new Scope(permission.scope())).isPresent()) {
                    return false;
                }
                createPermission.handle(new CreatePermissionCommand(permission.name(), permission.description(),
                        permission.scope()));
                return true;
            });
        }
        for (var role : ROLES) {
            created += attempt("role " + role.id(), () -> {
                if (roles.findById(new RoleId(role.id())).isPresent()) {
                    return false;
                }
                var permissionIds = role.scopes().stream()
                        .map(scope -> permissions.findIdByScope(new Scope(scope)).orElseThrow(
                                () -> new IllegalStateException("No permission for " + scope)))
                        .map(id -> String.valueOf(id.id()))
                        .toList();
                createRole.handle(new CreateRoleCommand(role.id(), role.name(), role.description(), permissionIds));
                return true;
            });
        }
        for (var employee : EMPLOYEES) {
            created += attempt("user " + employee.id(), () -> {
                if (users.getById(employee.id()).isPresent()) {
                    return false;
                }
                createUser.handle(new CreateUserCommand(employee.id(), employee.name(),
                        employee.id() + "@example.com", List.of(), employee.roles(), employee.attributes(), true));
                return true;
            });
        }
        created += attempt("user " + DEMO_USER, this::seedDemoUser);
        if (created > 0) {
            log.info("Demo seed: {} missing entr(y/ies) created", created);
        } else {
            log.debug("Demo seed: nothing missing");
        }
    }

    /**
     * The demo user exists in the realm import; here it may or may not. Created if absent (with the
     * realm's email, so the Keycloak update it causes changes nothing there); given its attributes if it
     * has none, and the content-admin role if it lacks it — and nothing else touched.
     */
    boolean seedDemoUser() {
        var existing = users.getById(DEMO_USER);
        if (existing.isEmpty()) {
            createUser.handle(new CreateUserCommand(DEMO_USER, "Demo User", "demo@mateu.io", List.of(),
                    List.of(CONTENT_ADMIN), DEMO_ATTRIBUTES, true));
            return true;
        }
        var demo = existing.get();
        var noAttributes = demo.attributes() == null || EmployeeAttributes.NONE.equals(demo.attributes());
        var roleIds = new ArrayList<>(demo.roleIds() == null ? List.<String>of() : demo.roleIds());
        var noAdmin = !roleIds.contains(CONTENT_ADMIN);
        if (!noAttributes && !noAdmin) {
            return false;
        }
        if (noAdmin) {
            roleIds.add(CONTENT_ADMIN);
        }
        var email = demo.email() == null || demo.email().isBlank() ? "demo@mateu.io" : demo.email();
        var name = demo.name() == null || demo.name().isBlank() ? "Demo User" : demo.name();
        saveUser.handle(new SaveUserCommand(demo.id(), name, email, demo.groupIds(), roleIds,
                noAttributes ? DEMO_ATTRIBUTES : demo.attributes()));
        return true;
    }

    interface Step {
        boolean run();
    }

    private int attempt(String what, Step step) {
        try {
            return step.run() ? 1 : 0;
        } catch (RuntimeException e) {
            log.warn("Demo seed: could not create {}: {}", what, e.toString());
            return 0;
        }
    }
}
