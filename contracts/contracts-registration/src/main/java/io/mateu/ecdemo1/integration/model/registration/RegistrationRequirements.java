package io.mateu.ecdemo1.integration.model.registration;

import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Field;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Moment;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.NationalityMatch;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Role;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Scope;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What the registration rules require of one guest — the one reading of {@link RegistrationRuleChanged}
 * that the control plane (its «Evaluar») and the front office (which enforces it) share, so both say
 * the same thing of the same guest.
 *
 * <p>A rule applies when it is active; its scope is the hotel's country or the hotel; the day is within
 * its dates; the moment is one of its moments; the guest's role matches; the nationality matches — an
 * unknown nationality matches (the rule is asked for until the nationality is known, and it is asked for
 * whenever a rule requires it); and the age on the arrival day is within its bounds — an unknown age
 * matches only a rule with no bounds, so a minors' rule is not asked of every guest whose birth date is
 * not known yet. Required: the union of what the applying rules require, less what an applying HOTEL
 * rule exempts.
 */
public final class RegistrationRequirements {

    /** The member states of the European Union, for the {@code EU} group. */
    public static final Set<String> EU = Set.of("AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR",
            "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE");

    private RegistrationRequirements() {
    }

    /** Who is registered, where and when. Any of the guest's data may be null: not known yet. */
    public record Guest(String hotelCode, String hotelCountry, Moment moment, LocalDate day, Role role,
                        String nationality, LocalDate birthDate) {
    }

    /** What is required of them, and why (the legal bases of the rules that applied, in order). */
    public record Result(Set<Field> fields, List<String> legalBases, List<String> ruleIds) {

        public boolean requires(Field field) {
            return fields.contains(field);
        }

        /** What of it is missing, given the values the guest has (by field; blank is missing). */
        public List<Field> missing(Map<Field, String> values) {
            var missing = new ArrayList<Field>();
            for (var f : fields) {
                var v = values == null ? null : values.get(f);
                if (v == null || v.isBlank()) {
                    missing.add(f);
                }
            }
            return missing;
        }
    }

    public static Result of(Collection<RegistrationRuleChanged> rules, Guest guest) {
        var required = EnumSet.noneOf(Field.class);
        var exempt = EnumSet.noneOf(Field.class);
        var bases = new LinkedHashSet<String>();
        var ids = new ArrayList<String>();
        for (var r : rules) {
            if (!applies(r, guest)) {
                continue;
            }
            ids.add(r.ruleId());
            if (r.requiredFields() != null) {
                required.addAll(r.requiredFields());
            }
            if (r.scope() == Scope.HOTEL && r.exemptFields() != null) {
                exempt.addAll(r.exemptFields());
            }
            if (r.legalBasis() != null && !r.legalBasis().isBlank()) {
                bases.add(r.legalBasis().trim());
            }
        }
        required.removeAll(exempt);
        return new Result(required, List.copyOf(bases), List.copyOf(ids));
    }

    public static boolean applies(RegistrationRuleChanged r, Guest g) {
        return r.active()
                && inScope(r, g)
                && (r.from() == null || g.day() == null || !g.day().isBefore(r.from()))
                && (r.to() == null || g.day() == null || !g.day().isAfter(r.to()))
                && (g.moment() == null || r.moments() == null || r.moments().isEmpty() || r.moments().contains(g.moment()))
                && roleMatches(r.role(), g.role())
                && nationalityMatches(r.nationalityMatch(), r.nationalities(), g.nationality())
                && ageMatches(r.minAge(), r.maxAge(), g.birthDate(), g.day());
    }

    static boolean inScope(RegistrationRuleChanged r, Guest g) {
        if (r.scopeCode() == null) {
            return false;
        }
        return r.scope() == Scope.HOTEL ? r.scopeCode().equalsIgnoreCase(nonNull(g.hotelCode()))
                : r.scopeCode().equalsIgnoreCase(nonNull(g.hotelCountry()));
    }

    static boolean roleMatches(Role rule, Role guest) {
        return rule == null || rule == Role.ANY || guest == null || rule == guest;
    }

    /** Whether a nationality is in a list that may name the {@code EU} group. */
    public static boolean inList(Collection<String> list, String nationality) {
        if (list == null || nationality == null) {
            return false;
        }
        var n = nationality.trim().toUpperCase(Locale.ROOT);
        for (var item : list) {
            var i = item == null ? "" : item.trim().toUpperCase(Locale.ROOT);
            if (i.equals(n) || ("EU".equals(i) && EU.contains(n))) {
                return true;
            }
        }
        return false;
    }

    static boolean nationalityMatches(NationalityMatch match, List<String> list, String nationality) {
        if (match == null || match == NationalityMatch.ANY) {
            return true;
        }
        if (nationality == null || nationality.isBlank()) {
            return true;
        }
        return match == NationalityMatch.IN == inList(list, nationality);
    }

    static boolean ageMatches(Integer min, Integer max, LocalDate birthDate, LocalDate day) {
        if (min == null && max == null) {
            return true;
        }
        if (birthDate == null) {
            return false;
        }
        var age = Period.between(birthDate, day == null ? LocalDate.now() : day).getYears();
        return (min == null || age >= min) && (max == null || age <= max);
    }

    /** How the desk names a field, in Spanish. */
    public static String label(Field f) {
        return switch (f) {
            case DOCUMENT_TYPE -> "tipo de documento";
            case DOCUMENT_NUMBER -> "número de documento";
            case DOCUMENT_ISSUING_COUNTRY -> "país de expedición del documento";
            case DOCUMENT_EXPIRY -> "caducidad del documento";
            case BIRTH_DATE -> "fecha de nacimiento";
            case BIRTH_PLACE -> "lugar de nacimiento";
            case NATIONALITY -> "nacionalidad";
            case SEX -> "sexo";
            case ADDRESS -> "dirección";
            case CITY -> "ciudad";
            case POSTAL_CODE -> "código postal";
            case COUNTRY_OF_RESIDENCE -> "país de residencia";
            case SIGNATURE -> "firma";
            case GUARDIAN -> "adulto responsable y parentesco";
        };
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }
}
