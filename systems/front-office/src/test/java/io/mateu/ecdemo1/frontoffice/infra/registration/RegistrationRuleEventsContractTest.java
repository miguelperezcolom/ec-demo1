package io.mateu.ecdemo1.frontoffice.infra.registration;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.ecdemo1.contracts.testing.Contracts;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged;
import org.junit.jupiter.api.Test;

/**
 * Every example of the registration-rules topic is read the way the front office's listener reads it
 * (its Jackson 3 mapper), whole: a country's rule and a hotel's, dates and age bounds included.
 */
class RegistrationRuleEventsContractTest {

  @Test
  void everyExampleOfRegistrationRulesIsReadWhole() {
    var examples = Contracts.topic("registration-rules").examples();
    assertThat(examples).isNotEmpty();
    var read = examples.stream().map(json -> RegistrationRuleEvents.read(json.getBytes())).toList();

    assertThat(read).allSatisfy(r -> {
      assertThat(r).isNotNull();
      assertThat(r.ruleId()).isNotBlank();
      assertThat(r.scope()).isNotNull();
      assertThat(r.scopeCode()).isNotBlank();
      assertThat(r.requiredFields()).isNotEmpty();
      assertThat(r.moments()).isNotEmpty();
    });
    assertThat(read).extracting(RegistrationRuleChanged::scope)
        .contains(RegistrationRuleChanged.Scope.COUNTRY, RegistrationRuleChanged.Scope.HOTEL);
    assertThat(read).anySatisfy(r -> assertThat(r.from()).isNotNull());
    assertThat(read).anySatisfy(r -> assertThat(r.minAge()).isNotNull());
    assertThat(read).anySatisfy(r -> assertThat(r.exemptFields()).isNotEmpty());
  }
}
