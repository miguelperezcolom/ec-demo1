package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PgEmployeeDirectory implements EmployeeDirectory {

    static final TypeReference<Map<Attribute, String>> ATTRIBUTES = new TypeReference<>() {
    };

    final JdbcTemplate jdbc;

    @Override
    public Optional<Employee> upsert(Employee e) {
        var old = find(e.userId());
        jdbc.update("insert into ch_employee (user_id, name, email, enabled, attributes, language, hash, updated_at) "
                        + "values (?, ?, ?, ?, ?::jsonb, ?, ?, ?) on conflict (user_id) do update set name = excluded.name, "
                        + "email = excluded.email, enabled = excluded.enabled, attributes = excluded.attributes, "
                        + "language = excluded.language, hash = excluded.hash, updated_at = excluded.updated_at",
                e.userId(), e.name(), e.email(), e.enabled(), Json.write(e.attributes().values()),
                e.attributes().language(), e.hash(), Timestamp.from(Instant.now()));
        return old;
    }

    @Override
    public Optional<Employee> remove(String userId) {
        var old = find(userId);
        jdbc.update("delete from ch_employee where user_id = ?", userId);
        return old;
    }

    @Override
    public Optional<Employee> find(String userId) {
        return jdbc.query("select user_id, name, email, enabled, attributes, language from ch_employee where user_id = ?",
                (rs, i) -> map(rs), userId).stream().findFirst();
    }

    @Override
    public List<Employee> all() {
        return jdbc.query("select user_id, name, email, enabled, attributes, language from ch_employee order by user_id",
                (rs, i) -> map(rs));
    }

    @Override
    public long count() {
        var n = jdbc.queryForObject("select count(*) from ch_employee", Long.class);
        return n == null ? 0 : n;
    }

    static Employee map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Employee(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4),
                new EmployeeAttributes(Json.read(rs.getString(5), ATTRIBUTES), rs.getString(6)));
    }
}
