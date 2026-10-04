package io.mateu.ecdemo1.integrations.ui.pages;

import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** An integration offers only the actions its state lets do something. */
class IntegrationActionsTest {

    static final List<String> LIFECYCLE = List.of("activate", "pause", "resume", "approveMapping", "relaunchBackfill",
            "recheck", "decommission");

    static IntegrationViewModel crsPms(String status) {
        var vm = new IntegrationViewModel(null, null);
        vm.id = "uuid";
        vm.status = new Status(StatusType.NONE, status);
        return vm;
    }

    static List<String> offered(IntegrationViewModel vm) {
        return LIFECYCLE.stream().filter(a -> !vm.isHidden(a, null)).toList();
    }

    @Test
    void anActiveIntegrationIsPausedNotActivatedNorResumed() {
        assertThat(offered(crsPms("ACTIVE"))).containsExactly("pause", "relaunchBackfill", "decommission");
    }

    @Test
    void aPausedOneIsResumed() {
        assertThat(offered(crsPms("PAUSED"))).containsExactly("resume", "relaunchBackfill", "decommission");
    }

    @Test
    void oneReadyToActivateIsActivated() {
        assertThat(offered(crsPms("READY_TO_ACTIVATE"))).containsExactly("activate", "decommission");
    }

    @Test
    void oneWaitingForItsMappingIsApprovedAndRechecked() {
        assertThat(offered(crsPms("MAPPING_PENDING"))).containsExactly("approveMapping", "recheck", "decommission");
    }

    @Test
    void aDecommissionedOneHasNothingLeft() {
        var vm = crsPms("DECOMMISSIONED");
        assertThat(offered(vm)).isEmpty();
        assertThat(vm.isHidden("verify", null)).isTrue();
    }

    @Test
    void aNewOneIsRegisteredFirst() {
        var vm = new IntegrationViewModel(null, null);
        assertThat(offered(vm)).isEmpty();
    }

    @Test
    void theFrontOfficeOnesFollowTheirOwnLifecycle() {
        var vm = new FrontOfficeIntegrationViewModel(null, null, null, null);
        vm.id = "uuid";
        vm.status = new Status(StatusType.NONE, "ACTIVE");
        assertThat(vm.isHidden("activate", null)).isTrue();
        assertThat(vm.isHidden("resume", null)).isTrue();
        assertThat(vm.isHidden("pause", null)).isFalse();
        vm.status = new Status(StatusType.NONE, "PAUSED");
        assertThat(vm.isHidden("resume", null)).isFalse();
        assertThat(vm.isHidden("pause", null)).isTrue();
        vm.status = new Status(StatusType.NONE, "DECOMMISSIONED");
        assertThat(vm.isHidden("decommission", null)).isTrue();
        assertThat(vm.isHidden("pollNow", null)).isTrue();
    }
}
