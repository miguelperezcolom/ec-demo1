package io.mateu.ecdemo1.integrations.ui.usage;

import io.mateu.ecdemo1.integration.model.usage.ApiUsage;
import io.mateu.ecdemo1.integrations.usage.ApiUsages;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.UI;
import io.mateu.uidl.data.MetricCard;
import io.mateu.uidl.data.MetricTrend;
import io.mateu.uidl.data.Scoreboard;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.ComponentTreeSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static io.mateu.ecdemo1.integrations.ui.usage.ApiUsagePage.TIME;
import static io.mateu.ecdemo1.integrations.ui.usage.ApiUsagePage.grouped;

/**
 * The external APIs' tiles on the consoles' home pages — what to look at before a meeting or a demo:
 * how many Salesforce calls are left of the org's daily allowance, whether that is going down or
 * coming back, our calls in the last hour against the hour before, when Salesforce last said it and
 * whether the MDM is holding its calls; and Opera's calls today, with what is left of its rate if OHIP
 * says.
 *
 * <p>A page view costs Salesforce nothing: these are the numbers the MDM already holds (its own
 * refresh of the org's limits stays at most every 15 minutes), through {@link ApiUsages}.
 */
@UI("/_api-usage/kpis")
@Title("")
@Service
@RequiredArgsConstructor
public class ApiUsageKpis implements ComponentTreeSupplier {

    final ApiUsages usages;

    @Override
    public Component component(HttpRequest httpRequest) {
        return tiles(usages.salesforce().orElse(null), usages.opera().orElse(null));
    }

    static Scoreboard tiles(ApiUsage salesforce, ApiUsage opera) {
        return Scoreboard.builder().style("width: 100%;")
                .metrics(cards(salesforce, opera))
                .build();
    }

    /**
     * The same three cards, for a console that lays them out itself: the shells' welcome pages
     * put them among their own tiles, where every renderer draws a KPI ({@code GET /usage/kpis}).
     */
    public List<MetricCard> cards() {
        return cards(usages.salesforce().orElse(null), usages.opera().orElse(null));
    }

    static List<MetricCard> cards(ApiUsage salesforce, ApiUsage opera) {
        return List.of(salesforce(salesforce), salesforcePace(salesforce), opera(opera));
    }

    /** "335 llamadas libres de 15.000", going down or coming back against an hour ago. */
    static MetricCard salesforce(ApiUsage u) {
        var card = MetricCard.builder().id("salesforce").title("SALESFORCE — LLAMADAS LIBRES").icon("vaadin:cloud");
        if (u == null || u.orgLeft() == null) {
            return card.value("—")
                    .description(u == null ? "El maestro de clientes no contesta"
                            : u.paused() ? "En pausa" + until(u) : "Salesforce aún no lo ha dicho")
                    .build();
        }
        card.value(grouped(u.orgLeft())).unit("de " + grouped(u.orgMax()));
        if (u.orgUsed1hAgo() != null) {
            var leftBefore = Math.max(0, u.orgMax() - u.orgUsed1hAgo());
            var change = u.orgLeft() - leftBefore;
            card.trend(change > 0 ? MetricTrend.up : change < 0 ? MetricTrend.down : MetricTrend.neutral)
                    .trendLabel((change > 0 ? "+" : "") + grouped(change) + " en la última hora");
        } else {
            card.trend(MetricTrend.neutral).trendLabel("sin dato de hace una hora");
        }
        return card.description((u.paused() ? "EN PAUSA" + until(u) + " · " : "")
                        + "visto " + (u.seenAt() == null ? "—" : TIME.format(u.seenAt())))
                .build();
    }

    /** Our calls in the last hour, against the hour before: whether the MDM is speeding up. */
    static MetricCard salesforcePace(ApiUsage u) {
        var card = MetricCard.builder().id("salesforce-pace").title("SALESFORCE — NUESTRAS, ÚLTIMA HORA").icon("vaadin:trending-up");
        if (u == null) {
            return card.value("—").description("El maestro de clientes no contesta").build();
        }
        var change = u.ours1h() - u.oursPrevious1h();
        return card.value(grouped(u.ours1h()))
                // Neutral: the card's arrow would point down for "worse" while the count goes up.
                .trend(MetricTrend.neutral)
                .trendLabel((change > 0 ? "↑ " : change < 0 ? "↓ " : "= ") + "frente a " + grouped(u.oursPrevious1h()) + " la hora anterior")
                .description(grouped(u.ours24h()) + " en 24 h" + (u.others24h() == null ? "" : " · otros: " + grouped(u.others24h())))
                .build();
    }

    /** "1.234 llamadas hoy", and what is left of OHIP's rate when it says. */
    static MetricCard opera(ApiUsage u) {
        var card = MetricCard.builder().id("opera").title("OPERA — LLAMADAS HOY").icon("vaadin:building");
        if (u == null) {
            return card.value("—").description("El conector con Opera no contesta").build();
        }
        var change = u.ours1h() - u.oursPrevious1h();
        card.value(grouped(u.ours24h()))
                .trend(u.limited24h() > 0 ? MetricTrend.down : MetricTrend.neutral)
                .trendLabel("última hora: " + grouped(u.ours1h()) + (change > 0 ? " ↑" : change < 0 ? " ↓" : ""));
        var said = u.rateLimitRemaining() == null ? "OHIP no dice su límite" : grouped(u.rateLimitRemaining()) + " libres según OHIP";
        return card.description((u.limited24h() > 0 ? grouped(u.limited24h()) + " × 429 · " : "") + said).build();
    }

    static String until(ApiUsage u) {
        return u.pausedUntil() == null ? "" : " hasta las " + TIME.format(u.pausedUntil());
    }
}
