package io.mateu.ecdemo1.integrations.store;

import io.mateu.ecdemo1.integration.model.integration.IntegrationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayDeque;
import java.util.EnumSet;

import static io.mateu.ecdemo1.integration.model.integration.IntegrationStatus.*;
import static io.mateu.ecdemo1.integrations.store.IntegrationTransition.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The integration's life, without a database: which moves it allows, and which it refuses. */
class IntegrationTransitionTest {

    static Integration at(IntegrationStatus status) {
        var i = new Integration();
        i.begin();
        // The shortest way there, through the machine itself.
        switch (status) {
            case CREATED -> {
            }
            case CONNECTIVITY_FAILED -> i.apply(CONNECTIVITY_FAILS);
            case PENDING_CONFIGURATION -> i.apply(PROPERTY_UNCONFIGURED);
            case MAPPING_PENDING -> i.apply(AWAIT_MAPPING);
            case SYNCING_PARTNERS -> path(i, AWAIT_MAPPING, SYNC_PARTNERS);
            case BACKFILL_BLOCKED -> path(i, AWAIT_MAPPING, SYNC_PARTNERS, BLOCK_BACKFILL);
            case BACKFILLING -> path(i, AWAIT_MAPPING, SYNC_PARTNERS, START_BACKFILL);
            case READY_TO_ACTIVATE -> path(i, AWAIT_MAPPING, SYNC_PARTNERS, START_BACKFILL, WINDOW_COVERED);
            case ACTIVE -> path(i, AWAIT_MAPPING, SYNC_PARTNERS, START_BACKFILL, WINDOW_COVERED, ACTIVATE);
            case PAUSED -> path(i, AWAIT_MAPPING, SYNC_PARTNERS, START_BACKFILL, WINDOW_COVERED, ACTIVATE, PAUSE);
            case DECOMMISSIONED -> i.apply(DECOMMISSION);
        }
        assertThat(i.getStatus()).isEqualTo(status);
        return i;
    }

    static void path(Integration i, IntegrationTransition... transitions) {
        for (var t : transitions) {
            i.apply(t);
        }
    }

    @Test
    void anIntegrationBeginsCreatedAndOnlyOnce() {
        var i = new Integration();
        assertThat(i.getStatus()).isNull();
        i.begin();
        assertThat(i.getStatus()).isEqualTo(CREATED);
        assertThatThrownBy(i::begin).isInstanceOf(IllegalStateException.class).hasMessageContaining("already begun");
    }

    @Test
    void nothingMovesAnIntegrationThatHasNotBegun() {
        for (var t : IntegrationTransition.values()) {
            assertThatThrownBy(() -> new Integration().apply(t)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void theOnboardingGoesFromCreatedToActiveThroughEveryGate() {
        var i = new Integration();
        i.begin();
        assertThat(i.apply(CONNECTIVITY_FAILS)).isEqualTo(CONNECTIVITY_FAILED);
        assertThat(i.apply(CONNECTIVITY_RESTORED)).isEqualTo(CREATED);
        assertThat(i.apply(PROPERTY_UNCONFIGURED)).isEqualTo(PENDING_CONFIGURATION);
        assertThat(i.apply(AWAIT_MAPPING)).isEqualTo(MAPPING_PENDING);
        assertThat(i.apply(SYNC_PARTNERS)).isEqualTo(SYNCING_PARTNERS);
        assertThat(i.apply(BLOCK_BACKFILL)).isEqualTo(BACKFILL_BLOCKED);
        assertThat(i.apply(START_BACKFILL)).isEqualTo(BACKFILLING);
        assertThat(i.apply(WINDOW_COVERED)).isEqualTo(READY_TO_ACTIVATE);
        assertThat(i.apply(ACTIVATE)).isEqualTo(ACTIVE);
        assertThat(i.apply(PAUSE)).isEqualTo(PAUSED);
        assertThat(i.apply(RESUME)).isEqualTo(ACTIVE);
        assertThat(i.apply(DECOMMISSION)).isEqualTo(DECOMMISSIONED);
    }

    @Test
    void aPropertyFoundUnconfiguredAgainSendsTheMappingBackToWaitForIt() {
        var i = at(MAPPING_PENDING);
        assertThat(i.apply(PROPERTY_UNCONFIGURED)).isEqualTo(PENDING_CONFIGURATION);
        assertThat(i.apply(AWAIT_MAPPING)).isEqualTo(MAPPING_PENDING);
    }

    @Test
    void aPersonCannotSkipTheLifeOfAnIntegration() {
        assertThatThrownBy(() -> at(READY_TO_ACTIVATE).apply(RESUME)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Only a paused integration can be resumed; it is READY_TO_ACTIVATE");
        assertThatThrownBy(() -> at(CREATED).apply(PAUSE)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Only an active integration can be paused; it is CREATED");
        assertThatThrownBy(() -> at(PAUSED).apply(PAUSE)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Only an active integration can be paused; it is PAUSED");
        assertThatThrownBy(() -> at(BACKFILLING).apply(ACTIVATE)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Only an integration ready to activate can be activated; it is BACKFILLING");
        assertThatThrownBy(() -> at(ACTIVE).apply(AWAIT_MAPPING)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> at(CONNECTIVITY_FAILED).apply(AWAIT_MAPPING)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @EnumSource(value = IntegrationStatus.class, names = "DECOMMISSIONED", mode = EnumSource.Mode.EXCLUDE)
    void anyIntegrationNotYetDecommissionedCanBe(IntegrationStatus status) {
        var i = at(status);
        assertThat(i.apply(DECOMMISSION)).isEqualTo(DECOMMISSIONED);
    }

    @Test
    void aDecommissionedIntegrationIsDoneNothingMovesIt() {
        for (var t : IntegrationTransition.values()) {
            var i = at(DECOMMISSIONED);
            assertThat(t.allowedFrom(DECOMMISSIONED)).isFalse();
            assertThatThrownBy(() -> i.apply(t)).isInstanceOf(IllegalStateException.class);
            assertThat(i.getStatus()).isEqualTo(DECOMMISSIONED);
        }
        assertThatThrownBy(() -> at(DECOMMISSIONED).apply(DECOMMISSION)).hasMessage("The integration is already DECOMMISSIONED");
    }

    @Test
    void aRefusedMoveLeavesTheStatusAsItWas() {
        var i = at(ACTIVE);
        assertThatThrownBy(() -> i.apply(RESUME)).isInstanceOf(IllegalStateException.class);
        assertThat(i.getStatus()).isEqualTo(ACTIVE);
    }

    @Test
    void everyStatusIsReachableFromTheStart() {
        var reached = EnumSet.of(IntegrationTransition.INITIAL);
        var queue = new ArrayDeque<IntegrationStatus>(reached);
        while (!queue.isEmpty()) {
            var from = queue.poll();
            for (var t : IntegrationTransition.values()) {
                if (t.allowedFrom(from) && reached.add(t.to())) {
                    queue.add(t.to());
                }
            }
        }
        assertThat(reached).containsExactlyInAnyOrder(IntegrationStatus.values());
    }
}
