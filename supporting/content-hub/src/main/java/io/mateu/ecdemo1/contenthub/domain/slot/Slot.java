package io.mateu.ecdemo1.contenthub.domain.slot;

import io.mateu.ecdemo1.contenthub.domain.shared.AggregateRoot;
import io.mateu.ecdemo1.contenthub.domain.shared.ContentEvents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A place in another application's screen where editorial content goes, by a stable key
 * ({@code application.screen.name}). What it is comes from the application's descriptor; what it shows,
 * from the editor. A slot that vanishes from the descriptors is marked orphan, never deleted: its
 * content may be wanted back.
 */
public class Slot extends AggregateRoot {

    private String key;
    private String application;
    private String screen;
    private String description;
    private String fallback;
    private boolean orphan;
    private List<SlotVariant> variants;

    @SuppressWarnings("unused") // for the store
    private Slot() {
    }

    public static Slot register(String key, String description, String fallback) {
        var parts = key == null ? new String[0] : key.split("\\.");
        if (parts.length < 3) {
            throw new IllegalArgumentException("La clave de un slot es aplicacion.pantalla.nombre: " + key);
        }
        var slot = new Slot();
        slot.key = key;
        slot.application = parts[0];
        slot.screen = parts[1];
        slot.description = description;
        slot.fallback = fallback;
        slot.variants = new ArrayList<>();
        return slot;
    }

    /** The descriptor says it again: refresh what it says, and it is no longer orphan. */
    public void describedAgain(String description, String fallback) {
        this.description = description;
        this.fallback = fallback;
        this.orphan = false;
    }

    public void markOrphan() {
        orphan = true;
    }

    public void putVariant(SlotVariant variant) {
        if (variants.stream().anyMatch(v -> v.priority() == variant.priority() && !v.id().equals(variant.id()))) {
            throw new IllegalArgumentException("Ya hay una variante con prioridad " + variant.priority());
        }
        variants.removeIf(v -> v.id().equals(variant.id()));
        variants.add(variant);
        raise(new ContentEvents.SlotVariantPublished(key));
    }

    public void removeVariant(String variantId) {
        if (variants.removeIf(v -> v.id().equals(variantId))) {
            raise(new ContentEvents.SlotVariantPublished(key));
        }
    }

    public String key() {
        return key;
    }

    public String application() {
        return application;
    }

    public String screen() {
        return screen;
    }

    public String description() {
        return description;
    }

    public String fallback() {
        return fallback;
    }

    public boolean orphan() {
        return orphan;
    }

    /** By priority, highest first. */
    public List<SlotVariant> variants() {
        return variants.stream().sorted(Comparator.comparingInt(SlotVariant::priority).reversed()).toList();
    }
}
