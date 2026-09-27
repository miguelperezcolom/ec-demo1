package io.mateu.ecdemo1.integrations.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static io.mateu.ecdemo1.integrations.store.FoIntegrationStatus.*;
import static io.mateu.ecdemo1.integrations.store.FoIntegrationTransition.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The pms-fo integration's life, without a database: which moves it allows, and which it refuses. */
class FoIntegrationTransitionTest {

    static FrontOfficeIntegration at(FoIntegrationStatus status) {
        var i = new FrontOfficeIntegration();
        i.begin();
        switch (status) {
            case CREATED -> {
            }
            case CONNECTIVITY_FAILED -> i.apply(CONNECTIVITY_FAILS);
            case SYNCING_CATALOGUE -> i.apply(SYNC_CATALOGUE);
            case BACKFILLING -> path(i, SYNC_CATALOGUE, START_BACKFILL);
            case READY_TO_ACTIVATE -> path(i, SYNC_CATALOGUE, START_BACKFILL, BACKFILL_DONE);
            case ACTIVE -> path(i, SYNC_CATALOGUE, START_BACKFILL, BACKFILL_DONE, ACTIVATE);
            case PAUSED -> path(i, SYNC_CATALOGUE, START_BACKFILL, BACKFILL_DONE, ACTIVATE, PAUSE);
            case DECOMMISSIONED -> i.apply(DECOMMISSION);
        }
        assertThat(i.getStatus()).isEqualTo(status);
        return i;
    }

    static void path(FrontOfficeIntegration i, FoIntegrationTransition... transitions) {
        for (var t : transitions) {
            i.apply(t);
        }
    }

    @Test
    void itBeginsCreatedAndOnlyOnce() {
        var i = new FrontOfficeIntegration();
        i.begin();
        assertThat(i.getStatus()).isEqualTo(CREATED);
        assertThatThrownBy(i::begin).isInstanceOf(IllegalStateException.class).hasMessageContaining("already begun");
    }

    @Test
    void theOnboardingGoesForwardGateByGate() {
        var i = at(CREATED);
        assertThat(i.apply(SYNC_CATALOGUE)).isEqualTo(SYNCING_CATALOGUE);
        assertThat(i.apply(START_BACKFILL)).isEqualTo(BACKFILLING);
        assertThat(i.apply(BACKFILL_DONE)).isEqualTo(READY_TO_ACTIVATE);
        assertThat(i.apply(ACTIVATE)).isEqualTo(ACTIVE);
        assertThat(i.apply(PAUSE)).isEqualTo(PAUSED);
        assertThat(i.apply(RESUME)).isEqualTo(ACTIVE);
    }

    @Test
    void aFailedConnectionIsRestoredToTheStart() {
        var i = at(CONNECTIVITY_FAILED);
        assertThatThrownBy(() -> i.apply(SYNC_CATALOGUE)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("once the connections work");
        assertThat(i.apply(CONNECTIVITY_RESTORED)).isEqualTo(CREATED);
    }

    @Test
    void nothingSkipsAGate() {
        assertThatThrownBy(() -> at(CREATED).apply(START_BACKFILL)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> at(SYNCING_CATALOGUE).apply(ACTIVATE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> at(BACKFILLING).apply(ACTIVATE)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ready to activate");
        assertThatThrownBy(() -> at(READY_TO_ACTIVATE).apply(PAUSE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> at(ACTIVE).apply(RESUME)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @EnumSource(FoIntegrationStatus.class)
    void decommissionedFromAnywhereButTheEndAndNothingLeavesIt(FoIntegrationStatus status) {
        var i = at(status);
        if (status == DECOMMISSIONED) {
            for (var t : FoIntegrationTransition.values()) {
                assertThatThrownBy(() -> i.apply(t)).isInstanceOf(IllegalStateException.class);
            }
        } else {
            assertThat(i.apply(DECOMMISSION)).isEqualTo(DECOMMISSIONED);
        }
    }
}
