package io.mateu.ecdemo1.contenthub.domain;

import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.visibility.VisibilityPolicy;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The projection's correctness, by generated cases: for any audience expression and any universe of
 * profiles, being in the projection's entry is exactly what evaluating the expression says — and the
 * TODOS token stands for exactly the audiences that restrict nothing.
 */
class VisibilityPolicyPropertiesTest {

    static final List<String> VALUES = List.of("A", "B", "C");

    @Provide
    Arbitrary<Audience> audiences() {
        return audience(3);
    }

    Arbitrary<Audience> audience(int depth) {
        Arbitrary<Audience> leaf = Combinators.combine(Arbitraries.of(Attribute.values()),
                        Arbitraries.of(VALUES).set().ofMinSize(1).ofMaxSize(2))
                .as(Audience.AttributeIn::new);
        Arbitrary<Audience> named = Arbitraries.of("n1", "n2", "gone").map(Audience.Named::new);
        if (depth == 0) {
            return Arbitraries.<Audience>frequencyOf(Tuple.of(6, leaf), Tuple.of(1, Arbitraries.just(Audience.EVERYONE)),
                    Tuple.of(1, named));
        }
        var sub = audience(depth - 1);
        Arbitrary<Audience> all = sub.list().ofMinSize(1).ofMaxSize(3).map(Audience.AllOf::new);
        Arbitrary<Audience> any = sub.list().ofMinSize(1).ofMaxSize(3).map(Audience.AnyOf::new);
        return Arbitraries.<Audience>frequencyOf(Tuple.of(3, leaf), Tuple.of(2, all), Tuple.of(2, any), Tuple.of(1, named),
                Tuple.of(1, Arbitraries.just(Audience.EVERYONE)));
    }

    @Provide
    Arbitrary<List<EmployeeAttributes>> universes() {
        Arbitrary<EmployeeAttributes> profile = Arbitraries.of(VALUES).optional().list().ofSize(Attribute.values().length)
                .map(values -> {
                    var map = new EnumMap<Attribute, String>(Attribute.class);
                    for (int i = 0; i < values.size(); i++) {
                        var v = values.get(i);
                        if (v.isPresent()) {
                            map.put(Attribute.values()[i], v.get());
                        }
                    }
                    return new EmployeeAttributes(map, "es");
                });
        return profile.list().ofMinSize(1).ofMaxSize(25);
    }

    static final Map<String, Audience> NAMED = Map.of(
            "n1", Audience.in(Attribute.HOTEL, "A"),
            "n2", Audience.anyOf(Audience.in(Attribute.REGION, "B"), new Audience.Named("n1")));

    final VisibilityPolicy policy = new VisibilityPolicy(NAMED::get);

    @Property(tries = 500)
    void the_projection_is_exactly_the_direct_evaluation(@ForAll("audiences") Audience audience,
                                                       @ForAll("universes") List<EmployeeAttributes> universe) {
        var hashes = policy.hashesSeeing(audience, universe);
        for (var profile : universe) {
            assertThat(VisibilityPolicy.seen(hashes, profile.hash()))
                    .as("%s for %s", audience, profile.canonical())
                    .isEqualTo(policy.admits(audience, profile));
        }
    }

    @Property(tries = 300)
    void the_effective_audience_never_widens_its_container(@ForAll("audiences") Audience container,
                                                          @ForAll("audiences") Audience own,
                                                          @ForAll("universes") List<EmployeeAttributes> universe) {
        var effective = Audience.effective(container, own);
        for (var profile : universe) {
            if (policy.admits(effective, profile)) {
                assertThat(policy.admits(container, profile)).isTrue();
                assertThat(policy.admits(own, profile)).isTrue();
            }
        }
    }

    @Property(tries = 200)
    void the_hash_ignores_the_language_and_normalises_values(@ForAll("universes") List<EmployeeAttributes> universe) {
        for (var p : universe) {
            assertThat(p.withLanguage("de").hash()).isEqualTo(p.hash());
            var lower = new EnumMap<Attribute, String>(Attribute.class);
            p.values().forEach((k, v) -> lower.put(k, " " + v.toLowerCase() + " "));
            assertThat(new EmployeeAttributes(lower, "en").hash()).isEqualTo(p.hash());
        }
    }

    @org.junit.jupiter.api.Test
    void a_deleted_named_audience_admits_nobody_and_a_cycle_is_cut() {
        var cyclic = new VisibilityPolicy(Map.<String, Audience>of("x", new Audience.Named("x"))::get);
        var anyone = new EmployeeAttributes(Map.of(Attribute.HOTEL, "A"), "es");
        assertThat(policy.admits(new Audience.Named("gone"), anyone)).isFalse();
        assertThat(cyclic.admits(new Audience.Named("x"), anyone)).isFalse();
        assertThat(policy.hashesSeeing(Audience.EVERYONE, List.of(anyone))).isEqualTo(Set.of("TODOS"));
    }
}
