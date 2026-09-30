package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.guest.CustomerNationalities;
import io.mateu.ecdemo1.frontoffice.domain.registration.PaxRegistrationData;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** What the desk scanned for the pax comes first; then the customer's; else nothing. */
class NationalitiesTest {

  final Map<String, Map<Field, String>> scanned = new HashMap<>();
  final Map<String, String> customers = new HashMap<>();

  final Nationalities nationalities = new Nationalities(new PaxRegistrationData() {
    @Override
    public Map<Field, String> of(String stayId, int pax) {
      return scanned.getOrDefault(stayId + "/" + pax, Map.of());
    }

    @Override
    public void put(String stayId, int pax, Map<Field, String> values) {
    }
  }, new CustomerNationalities() {
    @Override
    public Optional<String> of(String customerId) {
      return Optional.ofNullable(customers.get(customerId));
    }

    @Override
    public void put(String customerId, String nationality, String source) {
    }

    @Override
    public List<String> unknown(int limit) {
      return List.of();
    }
  });

  @Test
  void theScannedDocumentWinsOverTheCustomersRecord() {
    customers.put("C-1", "DE");
    scanned.put("S1/1", Map.of(Field.NATIONALITY, "AT"));
    assertThat(nationalities.of("S1", 1, "C-1")).isEqualTo("AT");
  }

  @Test
  void withNothingScannedItIsTheCustomers() {
    customers.put("C-2", "IT");
    assertThat(nationalities.of("S1", 2, "C-2")).isEqualTo("IT");
  }

  @Test
  void nobodySaidMeansNoNationality() {
    assertThat(nationalities.of("S1", 3, "C-3")).isNull();
    assertThat(nationalities.of(null, 1, null)).isNull();
  }
}
