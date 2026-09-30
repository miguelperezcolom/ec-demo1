package io.mateu.ecdemo1.integration.model.registration;

import io.mateu.ecdemo1.integration.model.registration.RegistrationRequirements.Guest;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Field;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Moment;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.NationalityMatch;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Role;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Scope;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the rules require of a guest: scope, dates, moment, role, nationality and age, and a hotel's exemptions. */
class RegistrationRequirementsTest {

    static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    static RegistrationRuleChanged rule(String id, Scope scope, String code, NationalityMatch match, List<String> nat,
                                        Integer min, Integer max, Role role, List<Field> required, List<Field> exempt) {
        return new RegistrationRuleChanged("E", null, id, 1, id, scope, code, match, nat, min, max, role, required, exempt,
                List.of(Moment.CHECK_IN), "base " + id, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), true);
    }

    static Guest guest(String hotel, String country, Role role, String nationality, LocalDate birth) {
        return new Guest(hotel, country, Moment.CHECK_IN, DAY, role, nationality, birth);
    }

    final RegistrationRuleChanged spain = rule("ES", Scope.COUNTRY, "ES", NationalityMatch.ANY, List.of(), 14, null,
            Role.ANY, List.of(Field.DOCUMENT_NUMBER, Field.NATIONALITY, Field.BIRTH_DATE, Field.ADDRESS), List.of());
    final RegistrationRuleChanged minors = rule("ES-MIN", Scope.COUNTRY, "ES", NationalityMatch.ANY, List.of(), null, 13,
            Role.ANY, List.of(Field.GUARDIAN, Field.BIRTH_DATE), List.of());
    final RegistrationRuleChanged nonEu = rule("NON-EU", Scope.COUNTRY, "ES", NationalityMatch.NOT_IN, List.of("EU"), null,
            null, Role.HOLDER, List.of(Field.DOCUMENT_EXPIRY, Field.DOCUMENT_ISSUING_COUNTRY), List.of());
    final RegistrationRuleChanged palma = rule("PMI01", Scope.HOTEL, "PMI01", NationalityMatch.ANY, List.of(), null, null,
            Role.ANY, List.of(Field.SEX), List.of(Field.ADDRESS));

    final List<RegistrationRuleChanged> all = List.of(spain, minors, nonEu, palma);

    @Test
    void theCountrysRuleAppliesToItsHotelsAndNotToOthers() {
        var r = RegistrationRequirements.of(all, guest("PMI02", "ES", Role.HOLDER, "FR", LocalDate.of(1980, 1, 1)));
        assertEquals(Set.of(Field.DOCUMENT_NUMBER, Field.NATIONALITY, Field.BIRTH_DATE, Field.ADDRESS), r.fields());
        assertEquals(List.of("base ES"), r.legalBases());
        assertTrue(RegistrationRequirements.of(all, guest("MRU01", "MU", Role.HOLDER, "FR", null)).fields().isEmpty());
    }

    @Test
    void aHotelsRuleAddsToItsCountrysAndMayExemptSome() {
        var r = RegistrationRequirements.of(all, guest("PMI01", "ES", Role.COMPANION, "DE", LocalDate.of(1980, 1, 1)));
        assertEquals(Set.of(Field.DOCUMENT_NUMBER, Field.NATIONALITY, Field.BIRTH_DATE, Field.SEX), r.fields());
    }

    @Test
    void nationalityInOrNotInTheEuAndAnUnknownOneIsAskedFor() {
        var british = RegistrationRequirements.of(all, guest("PMI02", "ES", Role.HOLDER, "GB", LocalDate.of(1980, 1, 1)));
        assertTrue(british.requires(Field.DOCUMENT_EXPIRY));
        var german = RegistrationRequirements.of(all, guest("PMI02", "ES", Role.HOLDER, "DE", LocalDate.of(1980, 1, 1)));
        assertFalse(german.requires(Field.DOCUMENT_EXPIRY));
        var unknown = RegistrationRequirements.of(all, guest("PMI02", "ES", Role.HOLDER, null, LocalDate.of(1980, 1, 1)));
        assertTrue(unknown.requires(Field.DOCUMENT_EXPIRY));
    }

    @Test
    void theRoleNarrowsARule() {
        var companion = RegistrationRequirements.of(all, guest("PMI02", "ES", Role.COMPANION, "GB", LocalDate.of(1980, 1, 1)));
        assertFalse(companion.requires(Field.DOCUMENT_EXPIRY));
    }

    @Test
    void ageBoundsAreInclusiveOnTheArrivalDayAndAnUnknownAgeMatchesOnlyUnboundedRules() {
        var turns14Today = RegistrationRequirements.of(all, guest("PMI02", "ES", Role.COMPANION, "ES", DAY.minusYears(14)));
        assertTrue(turns14Today.requires(Field.ADDRESS));
        assertFalse(turns14Today.requires(Field.GUARDIAN));
        var thirteen = RegistrationRequirements.of(all, guest("PMI02", "ES", Role.COMPANION, "ES", DAY.minusYears(14).plusDays(1)));
        assertTrue(thirteen.requires(Field.GUARDIAN));
        assertFalse(thirteen.requires(Field.ADDRESS));
        var unknownAge = RegistrationRequirements.of(all, guest("PMI02", "ES", Role.COMPANION, "ES", null));
        assertFalse(unknownAge.requires(Field.GUARDIAN));
        assertFalse(unknownAge.requires(Field.ADDRESS));
    }

    @Test
    void outsideItsDatesOrInactiveOrAtAnotherMomentARuleDoesNotApply() {
        var later = new Guest("PMI02", "ES", Moment.CHECK_IN, LocalDate.of(2027, 1, 1), Role.HOLDER, "ES", LocalDate.of(1980, 1, 1));
        assertTrue(RegistrationRequirements.of(all, later).fields().isEmpty());
        var online = new Guest("PMI02", "ES", Moment.ONLINE_CHECK_IN, DAY, Role.HOLDER, "ES", LocalDate.of(1980, 1, 1));
        assertTrue(RegistrationRequirements.of(all, online).fields().isEmpty());
        var off = new RegistrationRuleChanged("E", null, "OFF", 2, "off", Scope.COUNTRY, "ES", NationalityMatch.ANY, List.of(),
                null, null, Role.ANY, List.of(Field.SEX), List.of(), List.of(Moment.CHECK_IN), null, null, null, false);
        assertTrue(RegistrationRequirements.of(List.of(off), guest("PMI02", "ES", Role.HOLDER, "ES", null)).fields().isEmpty());
    }

    @Test
    void whatIsMissingIsWhatIsRequiredAndBlank() {
        var r = RegistrationRequirements.of(all, guest("PMI02", "ES", Role.COMPANION, "ES", LocalDate.of(1980, 1, 1)));
        var missing = r.missing(Map.of(Field.DOCUMENT_NUMBER, "X1", Field.NATIONALITY, "ES", Field.BIRTH_DATE, "1980-01-01",
                Field.ADDRESS, " "));
        assertEquals(List.of(Field.ADDRESS), missing);
    }
}
