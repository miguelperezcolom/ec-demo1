package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.RequestRepository;
import io.mateu.ecdemo1.contenthub.domain.request.EmployeeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PgRequestRepository implements RequestRepository {

    final JdbcTemplate jdbc;

    @Override
    public void save(EmployeeRequest r) {
        jdbc.update("insert into ch_request (id, employee_id, status, created_at, doc) values (?, ?, ?, ?, ?::jsonb) "
                        + "on conflict (id) do update set status = excluded.status, doc = excluded.doc",
                r.id(), r.employeeId(), r.status().name(), Timestamp.from(r.createdAt()), Json.write(r));
    }

    @Override
    public Optional<EmployeeRequest> find(String id) {
        return jdbc.query("select doc from ch_request where id = ?", (rs, i) -> Json.read(rs.getString(1), EmployeeRequest.class), id)
                .stream().findFirst();
    }

    @Override
    public List<EmployeeRequest> byEmployee(String employeeId) {
        return jdbc.query("select doc from ch_request where employee_id = ? order by created_at desc",
                (rs, i) -> Json.read(rs.getString(1), EmployeeRequest.class), employeeId);
    }
}
