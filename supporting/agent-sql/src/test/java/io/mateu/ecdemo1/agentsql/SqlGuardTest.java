package io.mateu.ecdemo1.agentsql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** What the guard lets through to the database, and what it explains to the model instead. */
class SqlGuardTest {

  final SqlGuard guard = new SqlGuard("agent");
  final Set<String> views = Set.of("stays", "guests");

  @Test
  void aSelectOverTheViewsQualifiedOrNot() {
    assertThat(guard.check("select s.id, g.name from stays s join agent.guests g on g.id = s.guest_id"
        + " where s.room_type ilike '%doble%' and g.nationality = 'ES'", views)).containsExactlyInAnyOrder("stays", "guests");
    assertThat(guard.check("with es as (select * from guests where nationality = 'ES')"
        + " select count(*) from stays join es on es.id = stays.guest_id", views)).containsExactlyInAnyOrder("stays", "guests");
    assertThat(guard.check("select nationality, count(*) from guests group by nationality"
        + " union all select 'total', count(*) from guests", views)).containsExactly("guests");
  }

  @Test
  void onlyTheViews() {
    assertThatThrownBy(() -> guard.check("select * from guest", views))
        .hasMessageContaining("Only the views of agent").hasMessageContaining("guests, stays");
    assertThatThrownBy(() -> guard.check("select * from public.stays", views)).hasMessageContaining("public.stays");
    assertThatThrownBy(() -> guard.check("select * from stays where id in (select stay_id from folio)", views))
        .hasMessageContaining("folio");
    assertThatThrownBy(() -> guard.check("select * from pg_catalog.pg_authid", views)).hasMessageContaining("pg_authid");
  }

  @Test
  void onlyOneSelect() {
    assertThatThrownBy(() -> guard.check("delete from stays", views)).hasMessageContaining("Only SELECT");
    assertThatThrownBy(() -> guard.check("update stays set status = 'X'", views)).hasMessageContaining("Only SELECT");
    assertThatThrownBy(() -> guard.check("select 1 from stays; drop table stays", views))
        .hasMessageContaining("Exactly one statement");
    assertThatThrownBy(() -> guard.check("", views)).hasMessageContaining("Empty SQL");
    assertThatThrownBy(() -> guard.check("selec * form stays", views)).hasMessageContaining("Not valid SQL");
  }

  @Test
  void noFunctionThatReachesOutsideTheData() {
    assertThatThrownBy(() -> guard.check("select set_config('role', 'workflow', true) from stays", views))
        .hasMessageContaining("set_config");
    assertThatThrownBy(() -> guard.check("select pg_read_file('/etc/passwd')", views))
        .hasMessageContaining("pg_read_file");
    assertThatThrownBy(() -> guard.check("select * from stays where pg_sleep(10) is null", views))
        .hasMessageContaining("pg_sleep");
    assertThat(guard.check("select lower(name), count(*), max(check_in) from guests join stays on true"
        + " group by lower(name)", views)).isNotEmpty();
  }
}
