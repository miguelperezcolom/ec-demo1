package io.mateu.ecdemo1.frontoffice.infra.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.ecdemo1.frontoffice.application.PmsStays;
import io.mateu.ecdemo1.messaging.MessagingProperties;
import io.mateu.ecdemo1.messaging.MessagingSchema;
import io.mateu.ecdemo1.messaging.Outbox;
import io.mateu.ecdemo1.messaging.OutboxMessage;
import io.mateu.ecdemo1.messaging.TraceContexts;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * What the front office's own outboxes and inbox still held when it moved to the shared ones is
 * carried over: the messages not relayed yet, in order, relayed from the shared outbox; the command
 * ids taken, still taken.
 */
class LegacyOutboxesTest {

  final DriverManagerDataSource db = new DriverManagerDataSource(
      "jdbc:h2:mem:legacy-outboxes;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE", "sa", "");
  final JdbcTemplate jdbc = new JdbcTemplate(db);
  final MessagingProperties messaging = MessagingProperties.defaults();

  @Test
  void whatWasNotRelayedMovesToTheSharedOutboxInOrderAndTheTakenIdsStayTaken() {
    // As schema.sql created them.
    jdbc.execute("create table audit_outbox (action_id varchar(64) primary key, payload varchar(8000) not null, "
        + "created_at timestamp not null, published_at timestamp)");
    jdbc.execute("create table command_outbox (message_id varchar(64) primary key, topic varchar(64) not null, "
        + "message_key varchar(200), payload varchar(8000) not null, created_at timestamp not null, published_at timestamp)");
    jdbc.execute("create table command_inbox (command_id varchar(64) primary key, taken_at timestamp not null)");
    jdbc.update("insert into command_outbox values ('C1', 'customer-commands', 'G1', '{\"n\":1}', timestamp '2026-09-28 10:00:00', null)");
    jdbc.update("insert into audit_outbox values ('A1', '{\"n\":2}', timestamp '2026-09-28 10:00:01', null)");
    jdbc.update("insert into command_outbox values ('C2', 'no-show-reports', 'MRU01/S1', '{\"n\":3}', timestamp '2026-09-28 10:00:02', null)");
    jdbc.update("insert into command_outbox values ('C0', 'customer-commands', 'G1', '{\"n\":0}', timestamp '2026-09-28 09:00:00', "
        + "timestamp '2026-09-28 09:00:01')");
    jdbc.update("insert into command_inbox values ('FC-1', timestamp '2026-09-28 08:00:00')");
    new MessagingSchema(jdbc, messaging).create();

    var legacy = new LegacyOutboxes(jdbc, new DataSourceTransactionManager(db), messaging);
    legacy.carryOver();
    legacy.carryOver();

    var outbox = new Outbox(jdbc, messaging, TraceContexts.NONE, Clock.systemUTC());
    assertThat(outbox.messages()).extracting(OutboxMessage::destination, OutboxMessage::key, OutboxMessage::payload)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("customer-commands", "G1", "{\"n\":1}"),
            org.assertj.core.groups.Tuple.tuple("audit", "A1", "{\"n\":2}"),
            org.assertj.core.groups.Tuple.tuple("no-show-reports", "MRU01/S1", "{\"n\":3}"));
    // Relayed once, from the shared outbox: gone from the old ones, whose history stays.
    assertThat(jdbc.queryForList("select message_id from command_outbox", String.class)).containsExactly("C0");
    assertThat(jdbc.queryForObject("select count(*) from audit_outbox", Integer.class)).isZero();
    var inbox = new io.mateu.ecdemo1.messaging.Inbox(jdbc, null, messaging, Clock.systemUTC(), false);
    assertThat(inbox.firstTime(PmsStays.CONSUMER, "FC-1")).isFalse();
    assertThat(inbox.firstTime(PmsStays.CONSUMER, "FC-2")).isTrue();
  }
}
