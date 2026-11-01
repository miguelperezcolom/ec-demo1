package io.mateu.ecdemo1.contenthub.domain.audience;

import io.mateu.ecdemo1.contenthub.domain.shared.AggregateRoot;
import io.mateu.ecdemo1.contenthub.domain.shared.ContentEvents;

/**
 * An audience with a name ("Recepción Canarias"), reused by reference. Redefining it changes what every
 * item, block and slot variant that names it is visible to — after the projection is recalculated.
 */
public class NamedAudience extends AggregateRoot {

    private String id;
    private String name;
    private Audience expression;

    @SuppressWarnings("unused") // for the store
    private NamedAudience() {
    }

    public static NamedAudience define(String id, String name, Audience expression) {
        var a = new NamedAudience();
        a.id = id;
        a.rename(name);
        a.expression = check(id, expression);
        return a;
    }

    public void rename(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Una audiencia con nombre necesita su nombre");
        }
        this.name = name.trim();
    }

    public void redefine(Audience expression) {
        this.expression = check(id, expression);
        raise(new ContentEvents.AudienceRedefined(id));
    }

    private static Audience check(String id, Audience expression) {
        var e = expression == null ? Audience.EVERYONE : expression;
        if (e.namedReferences().contains(id)) {
            throw new IllegalArgumentException("Una audiencia no puede referirse a sí misma");
        }
        return e;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Audience expression() {
        return expression;
    }
}
