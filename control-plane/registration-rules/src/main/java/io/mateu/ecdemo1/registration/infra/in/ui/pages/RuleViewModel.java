package io.mateu.ecdemo1.registration.infra.in.ui.pages;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Field;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Moment;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.NationalityMatch;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Role;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Scope;
import io.mateu.ecdemo1.registration.application.RegistrationRules;
import io.mateu.ecdemo1.registration.store.RegistrationRule;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.State;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import io.mateu.uidl.interfaces.OptionsSupplier;
import io.mateu.uidl.interfaces.VisibilitySupplier;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A registration rule's form. Choices as Strings with their options (OptionsSupplier), not enums: the
 * options say it in compliance's words, and a Set of an enum is drawn as a table, not as checkboxes.
 * The status starts non-null, as the other forms here: a null Status renders as text.
 */
@Service
@org.springframework.context.annotation.Scope("prototype")
@RequiredArgsConstructor
public class RuleViewModel implements Identifiable, OptionsSupplier, VisibilitySupplier {

    static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Europe/Madrid"));

    @ReadOnly
    @HiddenInCreate
    Status status = new Status(StatusType.NONE, "Nueva");

    @ReadOnly
    @HiddenInCreate
    String id;

    @Section("Dónde")
    @NotEmpty
    @Label("Nombre")
    String name;

    @NotEmpty
    @Stereotype(FieldStereotype.select)
    @Label("Ámbito")
    @Help("Los hoteles de un país, o un hotel. Una regla de hotel se suma a la de su país, y puede eximir de algún dato.")
    String scope = Scope.COUNTRY.name();

    @NotEmpty
    @Label("País o hotel")
    @Help("País: código ISO de dos letras (ES, MU…). Hotel: su código en el CRS (MRU01…).")
    String scopeCode;

    @Section("A quién")
    @Stereotype(FieldStereotype.select)
    @Label("Nacionalidad")
    String nationalityMatch = NationalityMatch.ANY.name();

    @Label("Nacionalidades")
    @Help("Códigos ISO separados por comas; EU es toda la Unión Europea. Una nacionalidad aún desconocida cuenta: "
            + "la regla se pide hasta saberla.")
    String nationalities;

    @Label("Edad mínima")
    @Help("En años cumplidos el día de llegada. Vacía, sin mínimo. Sin fecha de nacimiento, solo aplican las reglas sin edad.")
    Integer minAge;

    @Label("Edad máxima")
    Integer maxAge;

    @Stereotype(FieldStereotype.select)
    @Label("Huésped")
    String role = Role.ANY.name();

    @Section("Qué exige")
    @Stereotype(FieldStereotype.checkbox)
    @Label("Datos obligatorios")
    List<String> requiredFields = new ArrayList<>();

    @Stereotype(FieldStereotype.checkbox)
    @Label("Exime de (solo regla de hotel)")
    List<String> exemptFields = new ArrayList<>();

    @Stereotype(FieldStereotype.checkbox)
    @Label("Cuándo se piden")
    List<String> moments = new ArrayList<>(List.of(Moment.CHECK_IN.name(), Moment.ONLINE_CHECK_IN.name()));

    @Label("Base legal")
    @Help("Lo que lee recepción junto a los datos que faltan, p. ej. «RD 933/2021 · SES.Hospedajes».")
    String legalBasis;

    @Label("Desde")
    LocalDate from;

    @Label("Hasta")
    LocalDate to;

    @Label("Activa")
    boolean active = true;

    @Section("Cambios")
    @ReadOnly
    @HiddenInCreate
    @Label("Versión")
    Long version;

    @ReadOnly
    @HiddenInCreate
    @Label("Último cambio")
    String updated;

    final RegistrationRules rules;

    public String create(HttpRequest httpRequest) {
        return rules.create(draft(), who(httpRequest)).id;
    }

    public void save(HttpRequest httpRequest) {
        rules.update(id, draft(), who(httpRequest));
    }

    @Toolbar
    @Action
    public Object desactivar(HttpRequest httpRequest) {
        load(rules.setActive(id, false, who(httpRequest)));
        return List.of(new Message("Regla desactivada: los front office dejan de aplicarla"), new State(this));
    }

    @Toolbar
    @Action
    public Object activar(HttpRequest httpRequest) {
        load(rules.setActive(id, true, who(httpRequest)));
        return List.of(new Message("Regla activada"), new State(this));
    }

    RegistrationRules.Draft draft() {
        return new RegistrationRules.Draft(name, scope == null || scope.isBlank() ? null : Scope.valueOf(scope), scopeCode,
                nationalityMatch == null || nationalityMatch.isBlank() ? NationalityMatch.ANY : NationalityMatch.valueOf(nationalityMatch),
                nationalities == null ? List.of() : Arrays.stream(nationalities.split(",")).map(String::trim)
                        .filter(s -> !s.isEmpty()).toList(),
                minAge, maxAge, role == null || role.isBlank() ? Role.ANY : Role.valueOf(role),
                enums(requiredFields, Field.class), enums(exemptFields, Field.class), enums(moments, Moment.class),
                legalBasis, from, to, active);
    }

    static <E extends Enum<E>> Set<E> enums(List<String> names, Class<E> type) {
        var set = EnumSet.noneOf(type);
        if (names != null) {
            names.stream().filter(n -> n != null && !n.isBlank()).forEach(n -> set.add(Enum.valueOf(type, n)));
        }
        return set;
    }

    /** Deactivate an active rule, activate an inactive one — not before it exists. */
    @Override
    public boolean isHidden(String memberName, HttpRequest httpRequest) {
        return switch (memberName) {
            case "desactivar" -> id == null || !active;
            case "activar" -> id == null || active;
            default -> false;
        };
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return switch (fieldName) {
            case "scope" -> Labels.scopes();
            case "nationalityMatch" -> Labels.matches();
            case "role" -> Labels.roles();
            case "moments" -> Labels.moments();
            case "requiredFields", "exemptFields" -> Labels.fields();
            default -> List.of();
        };
    }

    public RuleViewModel load(RegistrationRule r) {
        id = r.id;
        status = r.active ? new Status(StatusType.SUCCESS, "Activa") : new Status(StatusType.NONE, "Inactiva");
        name = r.name;
        scope = r.scope;
        scopeCode = r.scopeCode;
        nationalityMatch = r.nationalityMatch().name();
        nationalities = String.join(", ", r.nationalityList());
        minAge = r.minAge;
        maxAge = r.maxAge;
        role = r.role().name();
        requiredFields = new ArrayList<>(r.requiredList().stream().map(Enum::name).toList());
        exemptFields = new ArrayList<>(r.exemptList().stream().map(Enum::name).toList());
        moments = new ArrayList<>(r.momentList().stream().map(Enum::name).toList());
        legalBasis = r.legalBasis;
        from = r.fromDate;
        to = r.toDate;
        active = r.active;
        version = r.version;
        updated = (r.updatedAt == null ? "" : WHEN.format(r.updatedAt)) + (r.updatedBy == null ? "" : " · " + r.updatedBy);
        return this;
    }

    /** Who is signed in, from the token the gateway let through; "consola" if it cannot be read. */
    static String who(HttpRequest httpRequest) {
        try {
            var header = httpRequest == null ? null : httpRequest.getHeaderValue("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                return "consola";
            }
            var parts = header.substring(7).split("\\.");
            var claims = new ObjectMapper().readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            var name = claims.path("name").asText(claims.path("preferred_username").asText(""));
            return name.isBlank() ? "consola" : name;
        } catch (RuntimeException | java.io.IOException e) {
            return "consola";
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return id == null ? "Nueva regla de registro" : name + " · " + (scope == null ? "" : scope.toLowerCase(Locale.ROOT)) + " " + scopeCode;
    }
}
