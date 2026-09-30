package io.mateu.ecdemo1.frontoffice.infra.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.ecdemo1.frontoffice.infra.audit.AuditOutbox;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** The confirmations on their own: turns, persons, expiry, single use and the audit of each outcome. */
class PendingConfirmationsTest {

  static class MutableClock extends Clock {
    Instant now = Instant.parse("2026-09-27T10:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  static class Caller implements McpCaller {
    String person;
    String turn;

    @Override
    public String person() {
      return person;
    }

    @Override
    public String turn() {
      return turn;
    }
  }

  final List<AuditedAction> audited = new ArrayList<>();
  final AuditOutbox outbox = new AuditOutbox(null) {
    @Override
    public void append(AuditedAction action) {
      audited.add(action);
    }
  };
  final MutableClock clock = new MutableClock();
  final Caller caller = new Caller();
  final PendingConfirmations confirmations = new PendingConfirmations(outbox, caller, "MRU01", clock);
  final AtomicInteger done = new AtomicInteger();

  String prepare() {
    var text = confirmations.prepare("Late check-out", "Late check-out para FO-1", Map.of("stayId", "FO-1"), () -> {
      done.incrementAndGet();
      return "contratado";
    });
    return FrontDeskMcpToolsTest.token(text);
  }

  @Test
  void aTokenExpires() {
    caller.person = "ana";
    caller.turn = "s1";
    var token = prepare();

    clock.now = clock.now.plus(Duration.ofMinutes(16));
    caller.turn = "s2";

    assertThat(confirmations.confirm(token)).contains("caducan");
    assertThat(done).hasValue(0);
    assertThat(audited).isEmpty();
  }

  @Test
  void theSameTurnIsRefusedAndTheNextOneDoesIt() {
    caller.person = "ana";
    caller.turn = "s1";
    var token = prepare();

    assertThat(confirmations.confirm(token)).startsWith("Error");
    assertThat(done).hasValue(0);

    caller.turn = "s2";
    assertThat(confirmations.confirm(token.toLowerCase())).isEqualTo("Hecho. contratado");
    assertThat(done).hasValue(1);
    assertThat(audited).singleElement().satisfies(a -> {
      assertThat(a.by()).isEqualTo("reception-agent (ana)");
      assertThat(a.service()).isEqualTo("front-office");
      assertThat(a.hotelCode()).isEqualTo("MRU01");
      assertThat(a.succeeded()).isTrue();
      assertThat(a.parameters()).contains("FO-1");
    });
  }

  @Test
  void withoutAPersonTheActorIsTheAgentAlone() {
    var token = prepare(); // no token forwarded, no session known: only the agent's instructions hold it back

    assertThat(confirmations.confirm(token)).startsWith("Hecho.");
    assertThat(audited).singleElement().extracting(AuditedAction::by).isEqualTo("reception-agent");
  }

  @Test
  void aFailedExecutionIsAuditedAsRefused() {
    caller.turn = "s1";
    var token = FrontDeskMcpToolsTest.token(confirmations.prepare("Room change", "Mover", Map.of(), () -> {
      throw new IllegalStateException("la habitación ya no está libre");
    }));
    caller.turn = "s2";

    assertThat(confirmations.confirm(token)).contains("la habitación ya no está libre");
    assertThat(audited).singleElement().satisfies(a -> {
      assertThat(a.succeeded()).isFalse();
      assertThat(a.response()).isEqualTo("la habitación ya no está libre");
    });
  }

  @Test
  void expiredOnesAreForgotten() {
    prepare();
    prepare();
    clock.now = clock.now.plus(Duration.ofHours(1));
    prepare();

    assertThat(confirmations.size()).isEqualTo(1);
  }

  @Test
  void anOperationThatAuditsItselfIsNotAuditedAgainAndNamesTheAgentForThePerson() {
    var records = new ArrayList<AuditedAction>();
    var counting = new AuditOutbox(null) {
      @Override
      protected void write(AuditedAction action) {
        records.add(action);
      }
    };
    var stayAudit = new io.mateu.ecdemo1.frontoffice.application.StayAudit(counting, null, "MRU01", null);
    var agent = new PendingConfirmations(counting, caller, "MRU01", clock);
    caller.person = "ana";
    caller.turn = "s1";
    var token = FrontDeskMcpToolsTest.token(agent.prepare("Room change", "Cambio a la 202", Map.of("stayId", "FO-9"),
        () -> stayAudit.run("Room change", "FO-9", null, Map.of("to", "202"), () -> "Habitación 202", r -> r)));
    caller.turn = "s2";

    assertThat(agent.confirm(token)).isEqualTo("Hecho. Habitación 202");
    assertThat(records).singleElement().satisfies(a -> {
      assertThat(a.action()).isEqualTo("Room change");
      assertThat(a.by()).isEqualTo("reception-agent (ana)");
      assertThat(a.parameters()).contains("FO-9", "202");
    });
  }
}
