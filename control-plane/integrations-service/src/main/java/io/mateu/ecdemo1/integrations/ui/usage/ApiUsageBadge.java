package io.mateu.ecdemo1.integrations.ui.usage;

import io.mateu.ecdemo1.integration.model.usage.ApiUsage;
import io.mateu.ecdemo1.integrations.usage.ApiUsages;
import io.mateu.ecdemo1.integrations.usage.UsageLevel;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.Trigger;
import io.mateu.uidl.annotations.TriggerType;
import io.mateu.uidl.annotations.UI;
import io.mateu.uidl.data.State;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.ComponentTreeSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Hydratable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The shells' header widget for the external APIs: "Salesforce 335 libres" — what is left of the org's
 * daily allowance, the figure to look at before a meeting or a demo, in the warning colour from 80%
 * used and the error colour from 95% or while the calls are paused — and "Opera N hoy", our calls to
 * OHIP in the last 24 hours (and what is left of its rate, if OHIP says), in the warning colour if it
 * answered 429. The tooltip says what they were for; a click opens the usage page.
 *
 * <p>Refreshed every minute, from numbers the services already hold ({@link ApiUsages}): an open
 * console costs Salesforce and Opera nothing. "—" when a service does not answer.
 */
@UI("/_api-usage/badge")
@Title("")
@Service
@RequiredArgsConstructor
@Trigger(type = TriggerType.OnLoad, actionId = "refresh", timeoutMillis = 60000)
@Trigger(type = TriggerType.OnSuccess, actionId = "refresh", calledActionId = "refresh", timeoutMillis = 60000)
@Action(id = "refresh")
public class ApiUsageBadge implements Hydratable, ComponentTreeSupplier {

    final ApiUsages usages;

    String content = "";

    Object refresh() {
        return new State(this);
    }

    @Override
    public void hydrate(HttpRequest httpRequest) {
        content = render(usages.salesforce(), usages.opera());
    }

    static String render(Optional<ApiUsage> salesforce, Optional<ApiUsage> opera) {
        var go = "event.preventDefault(); this.dispatchEvent(new CustomEvent('navigation-requested', {"
                + "detail: {route: '/usage/apis', consumedRoute: '', baseUrl: '/_api-usage', uriPrefix: '',"
                + " serverSideType: '" + ApiUsageHome.class.getName() + "'}, bubbles: true, composed: true}))";
        return "<a href=\"#\" onclick=\"" + go + "\" style=\"text-decoration: none; white-space: nowrap; color: inherit;\">"
                + chip("Salesforce", "SF", salesforceFigure(salesforce.orElse(null)), salesforce.orElse(null))
                + "<span style=\"display: var(--mateu-header-wide-only, inline)\">&nbsp;&nbsp;·&nbsp;&nbsp;</span>"
                + chip("Opera", "Opera", operaFigure(opera.orElse(null)), opera.orElse(null))
                + "</a>&nbsp;&nbsp;";
    }

    /** "335 libres" — what is left — "en pausa" while it is, or "—" when the MDM does not say. */
    static String salesforceFigure(ApiUsage u) {
        if (u == null || u.orgLeft() == null) {
            return u != null && u.paused() ? "en pausa" : "—";
        }
        return ApiUsages.compact(u.orgLeft()) + " libres";
    }

    /** "320 hoy", and what is left of OHIP's rate when it says: "320 hoy · 97 libres". */
    static String operaFigure(ApiUsage u) {
        if (u == null) {
            return "—";
        }
        return ApiUsages.compact(u.ours24h()) + " hoy"
                + (u.rateLimitRemaining() == null ? "" : " · " + ApiUsages.compact(u.rateLimitRemaining()) + " libres");
    }

    static String chip(String name, String shortName, String figure, ApiUsage usage) {
        var level = UsageLevel.of(usage);
        return "<span title=\"" + Html.escape(tooltip(name, usage)) + "\" style=\"color: " + level.color() + ";"
                + (level == UsageLevel.ERROR ? " font-weight: 600;" : "") + "\">"
                // A narrow header keeps a short name and the figure.
                + "<span style=\"display: var(--mateu-header-wide-only, inline)\">" + name + " </span>"
                + "<span style=\"display: var(--mateu-header-narrow-only, none)\">" + shortName + " </span>"
                + Html.escape(figure) + "</span>";
    }

    /** What the figure is made of, as a tooltip: the org's, ours, others', and ours by purpose. */
    static String tooltip(String name, ApiUsage u) {
        if (u == null) {
            return name + ": sin datos (el servicio que lo cuenta no contesta)";
        }
        var lines = new StringBuilder(name + " — últimas 24 h\n");
        if (u.orgLeft() != null) {
            lines.append("Libres: ").append(u.orgLeft()).append(" de ").append(u.orgMax()).append('\n');
            lines.append("Org: ").append(u.orgUsed()).append(" usadas").append('\n');
        }
        lines.append("Nuestras: ").append(u.ours24h()).append(" (última hora: ").append(u.ours1h()).append(")\n");
        if (u.others24h() != null) {
            lines.append("Otros: ").append(u.others24h()).append('\n');
        }
        if (u.limited24h() > 0) {
            lines.append("Rechazadas por límite: ").append(u.limited24h()).append('\n');
        }
        if (u.paused()) {
            lines.append("En pausa hasta ").append(u.pausedUntil()).append('\n');
        }
        lines.append(u.byPurpose().entrySet().stream().limit(6)
                .map(e -> "  " + e.getKey() + ": " + e.getValue()).collect(Collectors.joining("\n")));
        return lines.toString().trim();
    }

    @Override
    public Component component(HttpRequest httpRequest) {
        return Text.builder().text("${state.content}").build();
    }
}
