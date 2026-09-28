package io.mateu.ecdemo1.integrations.ui.usage;

import io.mateu.ecdemo1.integration.model.usage.ApiUsage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** What the header says: the calls left, their colour, their breakdown on hover, and "—" when nobody says. */
class ApiUsageBadgeTest {

    static ApiUsage salesforce(long used, boolean paused) {
        return new ApiUsage("salesforce", "customer-mdm", used, 15000L, Instant.EPOCH, 120, Map.of("poll", 100L, "refresh", 20L),
                null, used - 120, 0, 0, paused, null, null, null, 12, 30, null);
    }

    static ApiUsage opera(Long remaining) {
        return new ApiUsage("opera", "pms-integration", null, null, null, 320, Map.of("reservations", 320L), null, null,
                0, 0, false, null, null, remaining, 0, 0, null);
    }

    @Test
    void theCallsLeftOfTheOrgsAllowanceAndOurOperaCallsToday() {
        var html = ApiUsageBadge.render(Optional.of(salesforce(9000, false)), Optional.of(opera(null)));
        assertThat(html).contains(">6k libres</span>").contains(">320 hoy</span>")
                .contains(">SF </span>").contains("navigation-requested").contains("/_api-usage");
        // Hovering says whose calls they were, and on what.
        assertThat(html).contains("Libres: 6000 de 15000").contains("Nuestras: 120 (última hora: 12)")
                .contains("Otros: 8880").contains("poll: 100");
    }

    @Test
    void nearTheAllowanceTheFigureTurnsWarningThenError() {
        assertThat(ApiUsageBadge.render(Optional.of(salesforce(12500, false)), Optional.of(opera(null))))
                .contains("--lumo-warning-text-color").contains("2.5k libres");
        assertThat(ApiUsageBadge.render(Optional.of(salesforce(14665, false)), Optional.of(opera(null))))
                .contains("--lumo-error-text-color").contains(">335 libres</span>");
    }

    @Test
    void operaSaysWhatIsLeftOfItsRateWhenOhipSaysIt() {
        assertThat(ApiUsageBadge.render(Optional.empty(), Optional.of(opera(97L)))).contains("320 hoy · 97 libres");
    }

    @Test
    void whenTheServicesDoNotAnswerItSaysSoInsteadOfAWrongNumber() {
        var html = ApiUsageBadge.render(Optional.empty(), Optional.empty());
        assertThat(html).contains("Salesforce </span>").contains(">—</span>").contains("sin datos");
    }
}
