package io.mateu.ecdemo1.registration.infra.in.ui.pages;

import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Moment;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Role;
import io.mateu.ecdemo1.registration.application.RegistrationRules;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.State;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * «Probar con un huésped»: what the rules as they are now require of a guest like that — exactly what
 * the front office would ask of them at the desk. Before activating a rule, or to answer «¿qué se le pide
 * a un menor británico en Palma?».
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Getter
@Setter
@Title("Probar las reglas con un huésped")
public class EvaluateRules implements OptionsSupplier {

    @Section("El huésped")
    @Label("Hotel")
    @Help("Código del hotel en el CRS: MRU01, PMI01, CUN01…")
    String hotelCode = "MRU01";

    @Label("Nacionalidad")
    @Help("Código ISO de dos letras. Vacía: aún desconocida.")
    String nationality = "GB";

    @Label("Fecha de nacimiento")
    LocalDate birthDate;

    @Stereotype(FieldStereotype.select)
    @Label("Huésped")
    String role = Role.HOLDER.name();

    @Stereotype(FieldStereotype.select)
    @Label("Momento")
    String moment = Moment.CHECK_IN.name();

    @Label("Día de llegada")
    LocalDate day;

    @Section("Lo que se le exige")
    @io.mateu.uidl.annotations.Notice(theme = "info")
    String result = "Pulsa «Evaluar»: lo que las reglas activas exigen a este huésped, como se lo pediría el front office.";

    @Getter(lombok.AccessLevel.NONE)
    final RegistrationRules rules;

    @Toolbar
    @Action
    public Object evaluar(HttpRequest httpRequest) {
        var r = rules.evaluate(new RegistrationRules.Probe(hotelCode, moment == null || moment.isBlank() ? null : Moment.valueOf(moment),
                day, role == null || role.isBlank() ? null : Role.valueOf(role), nationality, birthDate));
        var country = rules.countryOf(hotelCode);
        if (r.fields().isEmpty()) {
            result = "Ninguna regla exige datos a este huésped en " + hotelCode + (country == null ? "" : " (" + country + ")") + ".";
        } else {
            result = "Datos obligatorios: " + String.join(", ", r.fields().stream().map(Labels::field).toList())
                    + ". Base legal: " + String.join("; ", r.legalBases())
                    + ". Reglas: " + String.join(", ", r.ruleIds())
                    + ". Hotel " + hotelCode + (country == null ? " (sin país conocido: solo reglas del hotel)" : " · " + country) + ".";
        }
        return List.of(new Message(r.fields().isEmpty() ? "No se le exige nada" : r.fields().size() + " dato(s) obligatorio(s)"),
                new State(this));
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return switch (fieldName) {
            case "role" -> List.of(new Option(Role.HOLDER.name(), Labels.role(Role.HOLDER)),
                    new Option(Role.COMPANION.name(), Labels.role(Role.COMPANION)));
            case "moment" -> Labels.moments();
            default -> List.of();
        };
    }
}
