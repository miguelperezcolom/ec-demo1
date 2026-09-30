package io.mateu.ecdemo1.frontoffice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mateu.ecdemo1.frontoffice.infra.audit.AuditOutbox;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Who did what to a stay, on its own: one record per operation, who, the failure, the refusal, the masking. */
class StayAuditTest {

  final List<AuditedAction> audited = new ArrayList<>();
  final AuditOutbox outbox = new AuditOutbox(null) {
    @Override
    protected void write(AuditedAction action) {
      audited.add(action);
    }
  };
  final StayAudit audit = new StayAudit(outbox, null, "MRU01", null,
      Clock.fixed(Instant.parse("2026-09-30T10:00:00Z"), ZoneOffset.UTC));

  static class Refused extends IllegalStateException implements StayAudit.AuditedRefusal {
    Refused(String why) {
      super(why);
    }
  }

  @Test
  void anOperationDoneIsOneRecordWithWhoWhatAndTheStay() {
    var result = StayAudit.as("ana", () -> audit.run("Room change", "FO-1", null,
        StayAudit.params("from", "101", "to", "202"), () -> "202", r -> "Habitación " + r));

    assertThat(result).isEqualTo("202");
    assertThat(audited).singleElement().satisfies(a -> {
      assertThat(a.action()).isEqualTo("Room change");
      assertThat(a.by()).isEqualTo("ana");
      assertThat(a.service()).isEqualTo("front-office");
      assertThat(a.hotelCode()).isEqualTo("MRU01");
      assertThat(a.succeeded()).isTrue();
      assertThat(a.response()).isEqualTo("Habitación 202");
      assertThat(a.parameters()).contains("\"stayId\":\"FO-1\"", "\"locator\":\"FO-1\"", "\"from\":\"101\"",
          "\"to\":\"202\"");
    });
  }

  @Test
  void aFailureIsAuditedWithWhyAndStillThrown() {
    assertThatThrownBy(() -> audit.run("Check-out", "FO-2", "luis", null, () -> {
      throw new IllegalStateException("Opera no responde");
    }, null)).hasMessage("Opera no responde");

    assertThat(audited).singleElement().satisfies(a -> {
      assertThat(a.by()).isEqualTo("luis");
      assertThat(a.succeeded()).isFalse();
      assertThat(a.response()).isEqualTo("Opera no responde");
    });
  }

  @Test
  void aRefusalAuditedWhereItWasDecidedIsNotAuditedAgain() {
    assertThatThrownBy(() -> audit.run("Check-in", "FO-3", "ana", null, () -> {
      throw new Refused("Aviso bloqueante sin leer");
    }, null)).isInstanceOf(Refused.class);

    assertThat(audited).isEmpty();
  }

  @Test
  void anOperationInsideAnotherIsPartOfIt() {
    audit.run("Check-in", "FO-4", "ana", null,
        () -> audit.run("Charge posted", "FO-4", "ana", null, () -> "ok", null), null);

    assertThat(audited).extracting(AuditedAction::action).containsExactly("Check-in");
  }

  @Test
  void whoIsWhomTheWorkRunsAsElseWhomTheCallerSaysElseTheDesk() {
    assertThat(StayAudit.actor("luis")).isEqualTo("luis");
    assertThat(StayAudit.as("reception-agent (ana)", () -> StayAudit.actor(null))).isEqualTo("reception-agent (ana)");
    assertThat(StayAudit.as("reception-agent (ana)", () -> StayAudit.actor("agente de recepción")))
        .isEqualTo("reception-agent (ana)");
    assertThat(StayAudit.actor(null)).isEqualTo("recepción"); // no token on this thread
  }

  @Test
  void cardsSecretsAndTokensAreMasked() {
    var masked = StayAudit.mask(Map.of(
        "cardNumber", "4111111111111111",
        "token", "abc",
        "note", "pagado con 4111 1111 1111 1234 en recepción",
        "companionName", "Luis",
        "payment", Map.of("cvv", "123", "method", "card")));

    assertThat(masked.get("cardNumber")).isEqualTo("***");
    assertThat(masked.get("token")).isEqualTo("***");
    assertThat(masked.get("note")).isEqualTo("pagado con ****1234 en recepción");
    assertThat(masked.get("companionName")).isEqualTo("Luis");
    @SuppressWarnings("unchecked")
    var payment = (Map<String, Object>) masked.get("payment");
    assertThat(payment).containsEntry("cvv", "***").containsEntry("method", "card");
  }

  @Test
  void theRecordNeverCarriesAMaskedValue() {
    audit.done("Payment taken", "FO-5", "ana", StayAudit.params("method", "card", "cardNumber", "4111111111111111"),
        "OK");

    assertThat(audited.getFirst().parameters()).doesNotContain("4111111111111111").contains("\"cardNumber\":\"***\"");
  }
}
