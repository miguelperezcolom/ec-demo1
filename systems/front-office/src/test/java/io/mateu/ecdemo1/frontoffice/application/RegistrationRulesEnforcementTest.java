package io.mateu.ecdemo1.frontoffice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.registration.PaxRegistrationData;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInOpsRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.PendingStep;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import io.mateu.ecdemo1.frontoffice.infra.crs.WalkInDesk;
import io.mateu.ecdemo1.frontoffice.infra.mcp.FrontDeskMcpTools;
import io.mateu.ecdemo1.frontoffice.infra.registration.RegistrationRuleEvents;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Field;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Moment;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.NationalityMatch;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Role;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Scope;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The destination's registration rules, kept as the control plane sends them and enforced by the
 * services: a check-in lacking what they require of a pax is refused — by the desk and by the reception
 * agent alike —, can be forced, and goes through once the kárdex has it; a walk-in's holder must carry
 * what they ask of it that the walk-in takes down. Through the real services (H2).
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:registration-rules;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
    "frontoffice.hotel=MRU01", "frontoffice.hotel-country=MU"})
class RegistrationRulesEnforcementTest {

  @Autowired RegistrationRequirementsService registration;
  @Autowired CheckInService checkIn;
  @Autowired KardexService kardex;
  @Autowired IncompleteCheckIns incomplete;
  @Autowired WalkInService walkIns;
  @Autowired FrontDeskMcpTools tools;
  @Autowired PaxRegistrationData data;
  @Autowired GuestRepository guests;
  @Autowired StayRepository stays;
  @Autowired RoomRepository rooms;
  @Autowired CheckInOpsRepository ops;
  @Autowired JdbcTemplate jdbc;

  static RegistrationRuleChanged rule(String id, long version, Scope scope, String code, NationalityMatch match,
                                      List<String> nationalities, Integer minAge, Role role, List<Field> required,
                                      boolean active) {
    return new RegistrationRuleChanged(UUID.randomUUID().toString(), Instant.now(), id, version, id, scope, code, match,
        nationalities, minAge, null, role, required, List.of(), List.of(Moment.CHECK_IN, Moment.ONLINE_CHECK_IN),
        "Ejemplo · " + id, null, null, active);
  }

  @BeforeEach
  void onlyThisTestsRules() {
    jdbc.update("delete from registration_rule");
    registration.take(rule("MU-ALL", 1, Scope.COUNTRY, "MU", NationalityMatch.ANY, List.of(), null, Role.ANY,
        List.of(Field.NATIONALITY, Field.BIRTH_DATE), true));
    registration.take(rule("MRU01-NON-EU", 1, Scope.HOTEL, "MRU01", NationalityMatch.NOT_IN, List.of("EU"), null,
        Role.HOLDER, List.of(Field.DOCUMENT_EXPIRY), true));
    registration.take(rule("ES-ONLY", 1, Scope.COUNTRY, "ES", NationalityMatch.ANY, List.of(), null, Role.ANY,
        List.of(Field.ADDRESS), true));
  }

  @Test
  void theRulesAreKeptOncePerEventAndTheHighestVersionWins() {
    var newer = rule("MU-ALL", 3, Scope.COUNTRY, "MU", NationalityMatch.ANY, List.of(), null, Role.ANY,
        List.of(Field.NATIONALITY), true);
    assertThat(registration.take(newer)).isTrue();
    assertThat(registration.take(newer)).isFalse(); // the same event again
    assertThat(registration.take(rule("MU-ALL", 2, Scope.COUNTRY, "MU", NationalityMatch.ANY, List.of(), null, Role.ANY,
        List.of(Field.SEX), true))).isFalse(); // an older version
    var kept = jdbc.queryForObject("select payload from registration_rule where rule_id = 'MU-ALL'", String.class);
    assertThat(RegistrationRuleEvents.read(kept.getBytes(StandardCharsets.UTF_8)).requiredFields())
        .containsExactly(Field.NATIONALITY);
  }

  @Test
  void aCheckInLackingWhatTheRulesRequireIsRefusedAtTheDeskAndSaysWhatAndWhy() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    Fixtures.complete(a.stayId(), guests, stays, ops);

    assertThatThrownBy(() -> checkIn.checkIn(a.stayId(), null, List.of(), "ana"))
        .isInstanceOf(IncompleteCheckIns.CheckInIncomplete.class)
        .hasMessageContaining("Datos de registro de")
        .hasMessageContaining("nacionalidad").hasMessageContaining("fecha de nacimiento")
        .hasMessageContaining("caducidad del documento")   // nationality unknown: asked until known
        .hasMessageNotContaining("dirección")               // Spain's rule: not this hotel's country
        .hasMessageContaining("Ejemplo · MU-ALL");
    assertThat(stays.findById(a.stayId()).orElseThrow().status()).isEqualTo(StayStatus.ARRIVING);
  }

  @Test
  void theReceptionAgentIsRefusedTheSame() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    Fixtures.complete(a.stayId(), guests, stays, ops);
    assertThat(tools.prepareCheckIn(a.stayId(), null, List.of())).contains("le falta").contains("Datos de registro");
  }

  @Test
  void onceTheKardexHasItTheCheckInGoesThroughAndAnEuNationalOwesNoDocumentExpiry() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    Fixtures.complete(a.stayId(), guests, stays, ops);
    kardex.registrationData(a.stayId(), 1, Map.of(Field.NATIONALITY, "de", Field.BIRTH_DATE, "1985-03-02"));
    assertThat(data.of(a.stayId(), 1)).containsEntry(Field.NATIONALITY, "de");
    assertThat(incomplete.missing(stays.findById(a.stayId()).orElseThrow()))
        .noneMatch(s -> s.kind() == PendingStep.Kind.REGISTRATION);

    var stay = checkIn.checkIn(a.stayId(), null, List.of(), "ana");
    assertThat(stay.status()).isEqualTo(StayStatus.IN_HOUSE);
  }

  @Test
  void aNonEuHolderOwesTheDocumentsExpiryAndACompanionDoesNot() {
    var a = Fixtures.arrival(guests, stays, rooms, 2);
    Fixtures.complete(a.stayId(), guests, stays, ops);
    kardex.registrationData(a.stayId(), 1, Map.of(Field.NATIONALITY, "GB", Field.BIRTH_DATE, "1985-03-02"));
    kardex.registrationData(a.stayId(), 2, Map.of(Field.NATIONALITY, "GB", Field.BIRTH_DATE, "1987-07-12"));
    var missing = incomplete.missing(stays.findById(a.stayId()).orElseThrow()).stream()
        .filter(s -> s.kind() == PendingStep.Kind.REGISTRATION).toList();
    assertThat(missing).singleElement().satisfies(s -> {
      assertThat(s.pax()).isEqualTo(1);
      assertThat(s.label()).contains("caducidad del documento");
    });
  }

  @Test
  void aForcedCheckInLetsTheGuestInOwingTheData() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    Fixtures.complete(a.stayId(), guests, stays, ops);
    var stay = checkIn.forceCheckIn(a.stayId(), null, List.of(), "El huésped vuelve con el pasaporte", "ana");
    assertThat(stay.status()).isEqualTo(StayStatus.IN_HOUSE);
    assertThat(incomplete.forced(a.stayId()).orElseThrow().missingWhenForced()).contains("Datos de registro");
  }

  @Test
  void anInactiveRuleIsNotApplied() {
    registration.take(rule("MU-ALL", 9, Scope.COUNTRY, "MU", NationalityMatch.ANY, List.of(), null, Role.ANY,
        List.of(Field.NATIONALITY, Field.BIRTH_DATE), false));
    registration.take(rule("MRU01-NON-EU", 9, Scope.HOTEL, "MRU01", NationalityMatch.NOT_IN, List.of("EU"), null,
        Role.HOLDER, List.of(Field.DOCUMENT_EXPIRY), false));
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    Fixtures.complete(a.stayId(), guests, stays, ops);
    assertThat(checkIn.checkIn(a.stayId(), null, List.of(), "ana").status()).isEqualTo(StayStatus.IN_HOUSE);
  }

  @Test
  void aWalkInsHolderMustCarryWhatTheRulesAskOfItThatTheWalkInTakesDown() {
    var holder = new WalkInDesk.Holder("Ana", "Pérez", null, null, null, "PASSPORT", "P123");
    assertThat(walkIns.missingFor(holder, LocalDate.now())).containsExactly("nacionalidad");
    var withNationality = new WalkInDesk.Holder("Ana", "Pérez", null, null, "FR", "PASSPORT", "P123");
    assertThat(walkIns.missingFor(withNationality, LocalDate.now())).isEmpty();
    assertThat(WalkInService.missing(new WalkInDesk.Holder("", "Pérez", null, null, "FR", null, null)))
        .containsExactly("nombre", "documento");
  }
}
