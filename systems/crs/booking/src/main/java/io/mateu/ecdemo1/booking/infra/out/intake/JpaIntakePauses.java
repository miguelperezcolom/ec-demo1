package io.mateu.ecdemo1.booking.infra.out.intake;

import io.mateu.ecdemo1.booking.application.out.intake.IntakePauses;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaIntakePauses implements IntakePauses {

    final IntakePauseRepository repository;

    @Override
    public Optional<Pause> current() {
        return repository.findById(IntakePauseEntity.ID)
                .map(e -> new Pause(e.getPausedUntil(), e.getPausedBy(), e.getProcessKey()));
    }

    @Override
    public void save(Pause pause) {
        var entity = repository.findById(IntakePauseEntity.ID).orElseGet(IntakePauseEntity::new);
        entity.setId(IntakePauseEntity.ID);
        entity.setPausedUntil(pause.until());
        entity.setPausedBy(pause.by());
        entity.setProcessKey(pause.processKey());
        repository.save(entity);
    }

    @Override
    public void clear() {
        repository.deleteById(IntakePauseEntity.ID);
    }
}
