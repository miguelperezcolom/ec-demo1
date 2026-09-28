package io.mateu.ecdemo1.shell.infra.in.ui;

import io.mateu.core.infra.declarative.orchestrators.welcome.Welcome;
import io.mateu.ecdemo1.uicommons.user.ApiUsageWidget;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Panel;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.MetricCard;

import java.net.URI;
import java.util.List;

/**
 * The data plane's home ({@link ShellHome#homeRoute()}): the welcome archetype — a hero, and under it
 * the external APIs' tiles: Salesforce's calls left of the org's daily allowance, our pace, and
 * Opera's calls today — what to look at before a meeting or a demo. Each opens the usage page.
 *
 * <p>A welcome page, and not a landing template with a micro frontend under it, because a welcome's
 * {@code @Panel} tiles are what both renderers draw as KPIs; Redwood draws no micro frontend in a
 * page's body. The page is built per request, so the tiles are the numbers of the moment, read from
 * integrations-service: rendering it costs Salesforce nothing.
 */
@Title("Data plane")
public class Inicio extends Welcome {

    final List<MetricCard> usage = ApiUsageWidget.kpis("verApisExternas");

    @Panel(title = "")
    MetricCard salesforce = usage.get(0);

    @Panel(title = "")
    MetricCard salesforcePace = usage.get(1);

    @Panel(title = "")
    MetricCard opera = usage.get(2);

    @Override
    protected String heroTitle() {
        return "EventConductor demo";
    }

    @Override
    protected String heroSubtitle() {
        return "Reservas y workflows, en una sola plataforma.";
    }

    @Override
    protected String heroImage() {
        return "/images/redwood-header-texture.png";
    }

    /** A tile opens the usage page: the breakdown by purpose, ours against others, the pause. */
    @Action
    Object verApisExternas() {
        return URI.create("/usage/apis");
    }
}
