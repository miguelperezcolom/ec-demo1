package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.slot.Slot;

import java.util.List;
import java.util.Optional;

public interface SlotRepository {

    Optional<Slot> find(String key);

    void save(Slot slot);

    List<Slot> findAll();
}
