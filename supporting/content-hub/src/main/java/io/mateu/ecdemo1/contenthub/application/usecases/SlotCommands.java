package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.events.DomainEvents;
import io.mateu.ecdemo1.contenthub.application.out.SlotRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.domain.slot.Slot;
import io.mateu.ecdemo1.contenthub.domain.slot.SlotVariant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

/** The slots: registered from the applications' descriptors, filled by the editors. */
@Service
@RequiredArgsConstructor
public class SlotCommands {

    final SlotRepository slots;
    final DomainEvents events;

    /** A slot as an application's descriptor declares it. */
    public record SlotDescriptor(String key, String description, String fallback) {
    }

    /**
     * Applies an application's descriptor: new slots are registered, known ones refreshed, and the
     * application's slots it no longer declares are marked orphan — never deleted.
     */
    @Transactional
    public void registerDescriptor(String application, List<SlotDescriptor> declared) {
        var keys = new HashSet<String>();
        for (var d : declared) {
            keys.add(d.key());
            var slot = slots.find(d.key()).orElse(null);
            if (slot == null) {
                slot = Slot.register(d.key(), d.description(), d.fallback());
            } else {
                slot.describedAgain(d.description(), d.fallback());
            }
            slots.save(slot);
        }
        for (var slot : slots.findAll()) {
            if (slot.application().equals(application) && !keys.contains(slot.key()) && !slot.orphan()) {
                slot.markOrphan();
                slots.save(slot);
            }
        }
    }

    @Transactional
    public void putVariant(Reader reader, String key, SlotVariant variant) {
        reader.require(EditorialPermission.ADMIN);
        var slot = slots.find(key).orElseThrow();
        slot.putVariant(variant);
        slots.save(slot);
        events.drain(slot);
    }

    @Transactional
    public void removeVariant(Reader reader, String key, String variantId) {
        reader.require(EditorialPermission.ADMIN);
        var slot = slots.find(key).orElseThrow();
        slot.removeVariant(variantId);
        slots.save(slot);
        events.drain(slot);
    }
}
