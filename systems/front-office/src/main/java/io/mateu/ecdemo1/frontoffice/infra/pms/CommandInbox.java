package io.mateu.ecdemo1.frontoffice.infra.pms;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The commands other services sent, by id: a command delivered twice is applied once. */
@Repository
public class CommandInbox {

  final JdbcTemplate jdbc;

  public CommandInbox(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** True the first time a command is taken — in the caller's transaction, so it counts only if applied. */
  public boolean take(String commandId, Instant at) {
    if (commandId == null || commandId.isBlank()) {
      return true;
    }
    var seen = jdbc.queryForObject("select count(*) from command_inbox where command_id = ?", Integer.class, commandId);
    if (seen != null && seen > 0) {
      return false;
    }
    jdbc.update("insert into command_inbox (command_id, taken_at) values (?, ?)", commandId, Timestamp.from(at));
    return true;
  }
}
