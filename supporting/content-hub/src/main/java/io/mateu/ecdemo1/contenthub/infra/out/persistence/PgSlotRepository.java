package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.SlotRepository;
import io.mateu.ecdemo1.contenthub.domain.slot.Slot;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PgSlotRepository implements SlotRepository {

    final JdbcTemplate jdbc;

    @Override
    public Optional<Slot> find(String key) {
        return jdbc.query("select doc from ch_slot where key = ?", (rs, i) -> Json.read(rs.getString(1), Slot.class), key)
                .stream().findFirst();
    }

    @Override
    public void save(Slot slot) {
        jdbc.update("insert into ch_slot (key, doc) values (?, ?::jsonb) on conflict (key) do update set doc = excluded.doc",
                slot.key(), Json.write(slot));
    }

    @Override
    public List<Slot> findAll() {
        return jdbc.query("select doc from ch_slot order by key", (rs, i) -> Json.read(rs.getString(1), Slot.class));
    }
}
