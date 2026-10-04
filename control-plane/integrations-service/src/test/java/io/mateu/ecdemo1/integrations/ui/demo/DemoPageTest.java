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
}
