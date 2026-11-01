package io.mateu.ecdemo1.contenthub.domain.shared;

import java.util.ArrayList;
import java.util.List;

/**
 * What every aggregate of the content service has: the events it raised since it was loaded, for the
 * use case to hand on after saving it. The domain's own, rather than the engine's AggregateRoot, so the
 * domain depends on nothing outside the JDK.
 */
public abstract class AggregateRoot {

    private transient List<DomainEvent> events;

    protected void raise(DomainEvent event) {
        if (events == null) {
            events = new ArrayList<>();
        }
        events.add(event);
    }

    /** The events raised since the last call, and forgets them. */
    public List<DomainEvent> popEvents() {
        var out = events == null ? List.<DomainEvent>of() : List.copyOf(events);
        events = null;
        return out;
    }
}
