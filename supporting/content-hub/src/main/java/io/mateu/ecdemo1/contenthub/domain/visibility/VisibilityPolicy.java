package io.mateu.ecdemo1.contenthub.domain.visibility;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * The only thing that evaluates audience expressions. Stateless: an audience, the attributes, and how
 * to resolve named audiences in, a yes or no out. Everything that enumerates content filters by the
 * projection this policy computes ({@link #hashesSeeing}), never by evaluating expressions itself.
 */
public final class VisibilityPolicy {

    private final Function<String, Audience> namedAudiences;

    /** @param namedAudiences a named audience's expression by id; null for one that no longer exists */
    public VisibilityPolicy(Function<String, Audience> namedAudiences) {
        this.namedAudiences = namedAudiences;
    }

    public boolean admits(Audience audience, EmployeeAttributes attributes) {
        return admits(audience, attributes, new ArrayDeque<>());
    }

    private boolean admits(Audience audience, EmployeeAttributes attributes, Deque<String> resolving) {
        return switch (audience) {
            case null -> true;
            case Audience.Everyone e -> true;
            case Audience.AttributeIn in -> attributes.get(in.attribute())
                    .map(v -> in.values().stream().anyMatch(x -> EmployeeAttributes.normalise(x).equals(v)))
                    .orElse(false);
            case Audience.AllOf all -> all.parts().stream().allMatch(p -> admits(p, attributes, resolving));
            case Audience.AnyOf any -> any.parts().stream().anyMatch(p -> admits(p, attributes, resolving));
            case Audience.Named named -> {
                // A named audience that was deleted admits nobody: content must never widen by accident.
                // One that refers back to itself is cut the same way.
                var resolved = namedAudiences.apply(named.audienceId());
                if (resolved == null || resolving.contains(named.audienceId())) {
                    yield false;
                }
                resolving.push(named.audienceId());
                try {
                    yield admits(resolved, attributes, resolving);
                } finally {
                    resolving.pop();
                }
            }
        };
    }

    /**
     * The projection's entry for one audience: the profile hashes, out of the universe, that it admits —
     * or just {@link EmployeeAttributes#EVERYONE_TOKEN} for an audience that restricts nothing, so a
     * profile that appears tomorrow sees it without a recalculation.
     */
    public Set<String> hashesSeeing(Audience audience, Collection<EmployeeAttributes> universe) {
        if (audience == null || audience.isEveryone(namedAudiences)) {
            return Set.of(EmployeeAttributes.EVERYONE_TOKEN);
        }
        var out = new LinkedHashSet<String>();
        for (var profile : universe) {
            if (admits(audience, profile)) {
                out.add(profile.hash());
            }
        }
        return out;
    }

    /** Whether a profile is in a projection entry. */
    public static boolean seen(Set<String> hashes, String hash) {
        return hashes.contains(EmployeeAttributes.EVERYONE_TOKEN) || hashes.contains(hash);
    }
}
