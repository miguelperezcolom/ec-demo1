package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.AcknowledgementRepository;
import io.mateu.ecdemo1.contenthub.domain.ack.Acknowledgement;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class PgAcknowledgementRepository implements AcknowledgementRepository {

    final JdbcTemplate jdbc;

    @Override
    public void record(Acknowledgement a) {
        jdbc.update("insert into ch_ack (item_id, version, user_id, type, at) values (?, ?, ?, ?, ?) on conflict do nothing",
                a.itemId(), a.version(), a.userId(), a.type().name(), Sql.ts(a.at()));
    }

    @Override
    public Set<String> users(String itemId, Acknowledgement.Type type, int minVersion) {
        return new HashSet<>(jdbc.queryForList(
                "select distinct user_id from ch_ack where item_id = ? and type = ? and version >= ?", String.class,
                itemId, type.name(), minVersion));
    }

    @Override
    public boolean has(String userId, String itemId, Acknowledgement.Type type, int minVersion) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from ch_ack where user_id = ? "
                + "and item_id = ? and type = ? and version >= ?)", Boolean.class, userId, itemId, type.name(), minVersion));
    }
}
