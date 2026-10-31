package io.mateu.ecdemo1.contenthub.domain.audience;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Who something is for: a tree of AND / OR whose leaves are {@code attribute IN (values)}, or a named,
 * reusable audience by id. {@link Everyone} is the empty audience — every employee.
 *
 * <p>Named audiences are kept as references, never copied in: redefining one changes everything that
 * names it, which is the point of naming it. They are resolved only when evaluated.
 */
public sealed interface Audience permits Audience.Everyone, Audience.AllOf, Audience.AnyOf, Audience.AttributeIn,
        Audience.Named {

    Everyone EVERYONE = new Everyone();

    record Everyone() implements Audience {
    }

    record AllOf(List<Audience> parts) implements Audience {
        public AllOf {
            parts = List.copyOf(parts);
        }
    }

    record AnyOf(List<Audience> parts) implements Audience {
        public AnyOf {
            parts = List.copyOf(parts);
        }
    }

    record AttributeIn(Attribute attribute, Set<String> values) implements Audience {
        public AttributeIn {
            if (attribute == null) {
                throw new IllegalArgumentException("A leaf needs its attribute");
            }
            values = Set.copyOf(new TreeSet<>(values));
        }
    }

    record Named(String audienceId) implements Audience {
        public Named {
            if (audienceId == null || audienceId.isBlank()) {
                throw new IllegalArgumentException("A named audience needs its id");
            }
        }
    }

    static Audience allOf(Audience... parts) {
        return new AllOf(List.of(parts));
    }

    static Audience anyOf(Audience... parts) {
        return new AnyOf(List.of(parts));
    }

    static Audience in(Attribute attribute, String... values) {
        return new AttributeIn(attribute, Set.of(values));
    }

    /**
     * The audience a contained thing actually has: its container's AND its own. Restrictive inheritance
     * is computed, never stored, so it holds even after a named audience is redefined.
     */
    static Audience effective(Audience container, Audience own) {
        if (container == null || container instanceof Everyone) {
            return own == null ? EVERYONE : own;
        }
        if (own == null || own instanceof Everyone) {
            return container;
        }
        return new AllOf(List.of(container, own));
    }

    /**
     * Whether nothing restricts it, once named audiences are resolved. A named audience that no longer
     * exists restricts everything — it admits nobody — so it is never "everyone".
     */
    default boolean isEveryone(Function<String, Audience> named) {
        return isEveryone(named, new java.util.HashSet<>());
    }

    private boolean isEveryone(Function<String, Audience> named, Set<String> resolving) {
        return switch (this) {
            case Everyone e -> true;
            case Named n -> {
                var resolved = named.apply(n.audienceId());
                yield resolved != null && resolving.add(n.audienceId()) && resolved.isEveryone(named, resolving);
            }
            case AllOf a -> a.parts().stream().allMatch(p -> p.isEveryone(named, resolving));
            default -> false;
        };
    }

    /** The named audiences this one refers to, directly or nested. */
    default Set<String> namedReferences() {
        return switch (this) {
            case Named n -> Set.of(n.audienceId());
            case AllOf a -> a.parts().stream().flatMap(p -> p.namedReferences().stream()).collect(Collectors.toSet());
            case AnyOf a -> a.parts().stream().flatMap(p -> p.namedReferences().stream()).collect(Collectors.toSet());
            default -> Set.of();
        };
    }

    /** Words for an editor: "Departamento ∈ {RECEPCION} y Región ∈ {CANARIAS}". */
    default String describe(Function<String, String> namedLabel) {
        return switch (this) {
            case Everyone e -> "Todos los empleados";
            case Named n -> "«" + namedLabel.apply(n.audienceId()) + "»";
            case AttributeIn in -> in.attribute().label() + " ∈ {" + String.join(", ", in.values()) + "}";
            case AllOf a -> a.parts().isEmpty() ? "Todos los empleados"
                    : a.parts().stream().map(p -> wrap(p, namedLabel)).collect(Collectors.joining(" y "));
            case AnyOf a -> a.parts().isEmpty() ? "Nadie"
                    : a.parts().stream().map(p -> wrap(p, namedLabel)).collect(Collectors.joining(" o "));
        };
    }

    private static String wrap(Audience part, Function<String, String> namedLabel) {
        var text = part.describe(namedLabel);
        return part instanceof AllOf || part instanceof AnyOf ? "(" + text + ")" : text;
    }
}
