package io.mateu.ecdemo1.integrations.ui.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.mateu.ecdemo1.integrations.demo.ResetRuns;
import org.junit.jupiter.api.Test;

class DemoPageTest {

    static ResetRuns.Run run(String status, List<ResetRuns.Step> steps) {
        return new ResetRuns.Run("p1", "reset-demo:x", status, Instant.parse("2026-10-03T17:00:00Z"),
                Instant.parse("2026-10-03T17:05:00Z"), 100, Map.of("launchedBy", "Ana"), "admin", steps);
    }

    static ResetRuns.Step step(String id, String status, int attempts) {
        return new ResetRuns.Step(id, "ACTION", status, attempts, null, null);
    }

    @Test
    void aCompletedResetSaysWhenAndWho() {
        var r = run("COMPLETED", List.of(step("not-confirmed", "CANCELLED", 0), step("notify-result", "COMPLETED", 0)));
        assertThat(DemoPage.headline(r)).startsWith("Último reset: completado a las 03/10 19:05:00")
                .contains("lanzado por Ana", "confirmado por admin");
        assertThat(DemoPage.theme(r)).isEqualTo("success");
    }

    @Test
    void aResetNobodyConfirmedSaysNothingChanged() {
        var r = run("COMPLETED", List.of(step("not-confirmed", "COMPLETED", 0)));
        assertThat(DemoPage.headline(r)).startsWith("Último reset: no confirmado, no cambió nada");
    }

    @Test
    void aFailedResetSaysWhichStepAndHowToRetry() {
        var r = run("ERROR", List.of(step("reset-erp", "COMPLETED", 0), step("clean-salesforce", "ERROR", 3)));
        assertThat(DemoPage.headline(r)).contains("en «clean-salesforce» (3 intentos)", "Reintentar el reset");
        assertThat(DemoPage.theme(r)).isEqualTo("error");
    }

    @Test
    void aResetWaitingForItsConfirmationSaysSo() {
        var r = run("RUNNING", List.of(new ResetRuns.Step("confirm", "USER_TASK", "PENDING", 0, null, null)));
        assertThat(DemoPage.headline(r)).startsWith("Reset esperando confirmación en la bandeja");
        assertThat(DemoPage.theme(r)).isEqualTo("warning");
    }

    @Test
    void theBannerIsARedPillOnlyWithItsText() {
        assertThat(DemoBanner.html("17:15")).contains("role=\"status\"", "Simulación: Opera no responde (hasta las 17:15)");
    }

    static DemoPage page(java.util.Optional<ResetRuns.Run> latest, java.util.Optional<ResetRuns.Run> open,
                         java.util.Optional<io.mateu.ecdemo1.integrations.demo.OperaOutage.Status> outage) {
        var runs = org.mockito.Mockito.mock(ResetRuns.class);
        org.mockito.Mockito.when(runs.available()).thenReturn(true);
        org.mockito.Mockito.when(runs.latest()).thenReturn(latest);
        org.mockito.Mockito.when(runs.open()).thenReturn(open);
        var opera = org.mockito.Mockito.mock(io.mateu.ecdemo1.integrations.demo.OperaOutage.class);
        org.mockito.Mockito.when(opera.status()).thenReturn(outage);
        return new DemoPage(runs, null, opera, null, null);
    }

    static java.util.Optional<io.mateu.ecdemo1.integrations.demo.OperaOutage.Status> outage(boolean on) {
        return java.util.Optional.of(new io.mateu.ecdemo1.integrations.demo.OperaOutage.Status(
                new io.mateu.ecdemo1.integrations.demo.OperaOutage.Outage(on, Instant.now(), Instant.now().plusSeconds(600), "Ana"),
                java.time.Duration.ofMinutes(2), java.time.Duration.ofMinutes(10), "ctx", "ref"));
    }

    @Test
    void theResetsActionsAreOfferedOnlyWhenTheyCanDoSomething() {
        var running = run("RUNNING", List.of(step("reset-erp", "RUNNING", 1)));
        var failed = run("ERROR", List.of(step("clean-salesforce", "ERROR", 3)));
        var done = run("COMPLETED", List.of(step("notify-result", "COMPLETED", 0)));

        var idle = page(java.util.Optional.of(done), java.util.Optional.empty(), outage(false));
        assertThat(idle.isHidden("cancelReset", null)).isTrue();
        assertThat(idle.isHidden("retryReset", null)).isTrue();
        assertThat(idle.isHidden("resetDemo", null)).isFalse();

        var inProgress = page(java.util.Optional.of(running), java.util.Optional.of(running), outage(false));
        assertThat(inProgress.isHidden("cancelReset", null)).isFalse();
        assertThat(inProgress.isHidden("retryReset", null)).isTrue();

        var inError = page(java.util.Optional.of(failed), java.util.Optional.empty(), outage(false));
        assertThat(inError.isHidden("retryReset", null)).isFalse();
    }

    @Test
    void theOutageSwitchOffersTheOtherState() {
        var off = page(java.util.Optional.empty(), java.util.Optional.empty(), outage(false));
        assertThat(off.isHidden("outageOn", null)).isFalse();
        assertThat(off.isHidden("outageOff", null)).isTrue();

        var on = page(java.util.Optional.empty(), java.util.Optional.empty(), outage(true));
        assertThat(on.isHidden("outageOn", null)).isTrue();
        assertThat(on.isHidden("outageOff", null)).isFalse();

        var unknown = page(java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        assertThat(unknown.isHidden("outageOn", null)).isFalse();
        assertThat(unknown.isHidden("outageOff", null)).isFalse();
    }

    @Test
    void everyActionDrawsThePageAgainNotOnlyItsState() {
        var page = page(java.util.Optional.empty(), java.util.Optional.empty(), outage(false));
        assertThat((List<?>) page.act(() -> "hecho")).hasSize(2).last().isSameAs(page);
        assertThat((List<?>) page.act(() -> { throw new IllegalStateException("no"); })).last().isSameAs(page);
        assertThat(page.refresh()).isSameAs(page);
    }
}
