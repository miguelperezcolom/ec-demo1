package io.mateu.ecdemo1.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.mateu.ecdemo1.contracts.testing.Contracts;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Field;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Moment;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.NationalityMatch;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Role;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Scope;
import io.mateu.ecdemo1.registration.application.RegistrationRules;
import io.mateu.ecdemo1.registration.infra.out.RuleOutbox;
import io.mateu.ecdemo1.registration.store.RegistrationRule;
import io.mateu.ecdemo1.registration.store.RegistrationRuleRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rules kept in the control plane: every change published whole (the schema of registration-rules)
 * and audited with who made it; what cannot be a rule refused; and «Probar» saying what the front
 * office would ask.
 */
class RegistrationRulesTest {

    final Map<String, RegistrationRule> store = new LinkedHashMap<>();
    final List<String[]> outbox = new ArrayList<>();
    final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    final RuleOutbox events = new RuleOutbox((binding, key, type, payload) -> outbox.add(new String[]{binding, key, type, payload}), mapper);
    final RegistrationRuleRepository repository = (RegistrationRuleRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{RegistrationRuleRepository.class}, (proxy, method, args) ->
                    switch (method.getName()) {
                        case "save" -> {
                            var r = (RegistrationRule) args[0];
                            store.put(r.id, r);
                            yield r;
                        }
                        case "findById" -> Optional.ofNullable(store.get((String) args[0]));
                        case "findAll" -> new ArrayList<>(store.values());
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
    final RegistrationRules rules = new RegistrationRules(repository, events,
            new RegistrationRules.HotelCountries(Map.of("MRU01", "MU", "PMI01", "ES")),
            Clock.fixed(Instant.parse("2026-10-01T09:00:00Z"), ZoneOffset.UTC));

    static RegistrationRules.Draft spain() {
        return new RegistrationRules.Draft("España · registro de viajeros", Scope.COUNTRY, "es", NationalityMatch.ANY,
                List.of(), 14, null, Role.ANY, Set.of(Field.DOCUMENT_NUMBER, Field.BIRTH_DATE, Field.ADDRESS), Set.of(),
                Set.of(Moment.CHECK_IN), "RD 933/2021", null, null, true);
    }

    @Test
    void aRuleIsPublishedWholeAndAuditedWithWhoMadeIt() {
        var r = rules.create(spain(), "Ana");
        assertThat(r.scopeCode).isEqualTo("ES");
        assertThat(outbox).extracting(m -> m[0]).containsExactly(RuleOutbox.RULES, RuleOutbox.AUDIT);
        assertThat(outbox.get(0)[1]).isEqualTo("COUNTRY:ES");
        Contracts.topic("registration-rules").assertValid(outbox.get(0)[3]);
        Contracts.topic("audit").assertValid(outbox.get(1)[3]);
        assertThat(outbox.get(1)[3]).contains("\"by\":\"Ana\"").contains("Registration rule created");

        rules.setActive(r.id, false, "Luis");
        assertThat(store.get(r.id).version).isEqualTo(2);
        assertThat(outbox.get(3)[3]).contains("\"by\":\"Luis\"").contains("deactivated");
    }

    @Test
    void whatCannotBeARuleIsRefused() {
        assertThatThrownBy(() -> rules.create(new RegistrationRules.Draft("x", Scope.COUNTRY, "SPAIN", NationalityMatch.ANY,
                List.of(), null, null, Role.ANY, Set.of(Field.SEX), Set.of(), Set.of(Moment.CHECK_IN), null, null, null, true), "a"))
                .hasMessageContaining("ISO");
        assertThatThrownBy(() -> rules.create(new RegistrationRules.Draft("x", Scope.COUNTRY, "ES", NationalityMatch.ANY,
                List.of(), null, null, Role.ANY, Set.of(Field.SEX), Set.of(Field.ADDRESS), Set.of(Moment.CHECK_IN), null, null,
                null, true), "a")).hasMessageContaining("hotel");
        assertThatThrownBy(() -> rules.create(new RegistrationRules.Draft("x", Scope.HOTEL, "MRU01", NationalityMatch.NOT_IN,
                List.of(), null, null, Role.ANY, Set.of(Field.SEX), Set.of(), Set.of(Moment.CHECK_IN), null, null, null, true), "a"))
                .hasMessageContaining("nacionalidades");
        assertThatThrownBy(() -> rules.create(new RegistrationRules.Draft("x", Scope.HOTEL, "MRU01", NationalityMatch.ANY,
                List.of(), 18, 10, Role.ANY, Set.of(Field.SEX), Set.of(), Set.of(Moment.CHECK_IN), null, null, null, true), "a"))
                .hasMessageContaining("edad");
        assertThat(outbox).isEmpty();
    }

    @Test
    void probingSaysWhatTheFrontOfficeWouldAskUsingTheHotelsCountry() {
        rules.create(spain(), "Ana");
        rules.create(new RegistrationRules.Draft("Palma", Scope.HOTEL, "PMI01", NationalityMatch.NOT_IN, List.of("EU"), null,
                null, Role.HOLDER, Set.of(Field.DOCUMENT_EXPIRY), Set.of(Field.ADDRESS), Set.of(Moment.CHECK_IN), "Ejemplo",
                null, null, true), "Ana");
        var briton = rules.evaluate(new RegistrationRules.Probe("pmi01", null, null, Role.HOLDER, "gb", LocalDate.of(1980, 1, 1)));
        assertThat(briton.fields()).containsExactlyInAnyOrder(Field.DOCUMENT_NUMBER, Field.BIRTH_DATE, Field.DOCUMENT_EXPIRY);
        assertThat(rules.evaluate(new RegistrationRules.Probe("MRU01", null, null, Role.HOLDER, "GB", null)).fields()).isEmpty();
    }

    @Test
    void republishingSendsEveryRuleAgainAsItIs() {
        rules.create(spain(), "Ana");
        outbox.clear();
        assertThat(rules.republishAll()).isEqualTo(1);
        assertThat(outbox).extracting(m -> m[2]).containsExactly("RegistrationRuleChanged");
        assertThat(outbox.get(0)[3]).contains("\"version\":1");
    }

    @Test
    void anAuditedActionIsTheContractsRecord() {
        assertThat(AuditedAction.class).isNotNull();
        assertThat(RegistrationRuleChanged.TOPIC).isEqualTo("registration-rules");
    }
}
