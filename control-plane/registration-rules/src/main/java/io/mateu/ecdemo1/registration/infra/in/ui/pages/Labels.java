package io.mateu.ecdemo1.registration.infra.in.ui.pages;

import io.mateu.ecdemo1.integration.model.registration.RegistrationRequirements;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Field;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Moment;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.NationalityMatch;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Role;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Scope;
import io.mateu.uidl.data.Option;

import java.util.Arrays;
import java.util.List;

/** The rules' choices in the words of whoever keeps them. */
final class Labels {

    private Labels() {
    }

    static String scope(Scope s) {
        return s == Scope.COUNTRY ? "País" : "Hotel";
    }

    static String match(NationalityMatch m) {
        return switch (m) {
            case ANY -> "Cualquier nacionalidad";
            case IN -> "Solo estas nacionalidades";
            case NOT_IN -> "Todas menos estas";
        };
    }

    static String role(Role r) {
        return switch (r) {
            case ANY -> "Cualquier huésped";
            case HOLDER -> "El titular";
            case COMPANION -> "Los acompañantes";
        };
    }

    static String moment(Moment m) {
        return switch (m) {
            case PRE_ARRIVAL -> "Antes de la llegada";
            case CHECK_IN -> "Check-in en recepción";
            case ONLINE_CHECK_IN -> "Check-in online / app";
        };
    }

    static String field(Field f) {
        var l = RegistrationRequirements.label(f);
        return Character.toUpperCase(l.charAt(0)) + l.substring(1);
    }

    static List<Option> scopes() {
        return Arrays.stream(Scope.values()).map(s -> new Option(s.name(), scope(s))).toList();
    }

    static List<Option> matches() {
        return Arrays.stream(NationalityMatch.values()).map(m -> new Option(m.name(), match(m))).toList();
    }

    static List<Option> roles() {
        return Arrays.stream(Role.values()).map(r -> new Option(r.name(), role(r))).toList();
    }

    static List<Option> moments() {
        return Arrays.stream(Moment.values()).map(m -> new Option(m.name(), moment(m))).toList();
    }

    static List<Option> fields() {
        return Arrays.stream(Field.values()).map(f -> new Option(f.name(), field(f))).toList();
    }
}
