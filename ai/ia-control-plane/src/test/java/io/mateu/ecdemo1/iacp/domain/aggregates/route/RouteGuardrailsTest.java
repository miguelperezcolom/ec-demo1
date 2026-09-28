package io.mateu.ecdemo1.iacp.domain.aggregates.route;

import io.mateu.ecdemo1.iacp.domain.aggregates.route.vo.Guardrails;
import io.mateu.ecdemo1.iacp.domain.aggregates.route.vo.RouteId;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Enabled;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A route's guardrails: ordered, once each, never its own target, CLOSED unless said otherwise. */
class RouteGuardrailsTest {

    static Route route(String target, Guardrails guardrails) {
        return Route.of(new RouteId("r"), new Name("r"), 10, null, null, null, null, target, guardrails);
    }

    @Test
    void guardrailsKeepTheirOrderOnceEach() {
        var r = route("front", new Guardrails(List.of("b", "a", "b", " "), List.of("c"), null));
        assertThat(r.getGuardrails().input()).containsExactly("b", "a");
        assertThat(r.getGuardrails().output()).containsExactly("c");
        assertThat(r.getGuardrails().failure()).isEqualTo(Guardrails.Failure.CLOSED);
    }

    @Test
    void theTargetAgentCannotBeItsOwnGuardrailOnEitherSide() {
        assertThatThrownBy(() -> route("front", new Guardrails(List.of("front"), List.of(), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot also be one of its guardrails");
        assertThatThrownBy(() -> route("front", new Guardrails(List.of(), List.of("front"), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void norCanAnUpdateMakeItOneAndARefusedUpdateChangesNothing() {
        var r = route("front", new Guardrails(List.of("guard"), List.of(), null));
        assertThatThrownBy(() -> r.update(new Name("renamed"), 1, null, null, null, null, "guard",
                r.getGuardrails(), Enabled.yes()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(r.getName().value()).isEqualTo("r");
        assertThat(r.getTargetAgentId()).isEqualTo("front");
    }

    @Test
    void theFailureModeIsClosedByDefaultAndOnlyClosedOrOpen() {
        assertThat(Guardrails.Failure.parse(null)).isEqualTo(Guardrails.Failure.CLOSED);
        assertThat(Guardrails.Failure.parse(" open ")).isEqualTo(Guardrails.Failure.OPEN);
        assertThatThrownBy(() -> Guardrails.Failure.parse("SOMETIMES")).isInstanceOf(IllegalArgumentException.class);
        assertThat(route("front", null).getGuardrails()).isEqualTo(Guardrails.none());
    }
}
