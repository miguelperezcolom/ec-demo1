package io.mateu.ecdemo1.shell.infra.in.ui;

import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.fluent.Component;
import io.mateu.ecdemo1.uicommons.content.ContentSlots;
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

    /** The content slot this home shows (content-hub; editors fill it from "Editorial"). */
    static final String SLOT = "data-plane.inicio.novedades";

    /**
     * What the editors publish for whoever opens the data plane — news and notices for their hotel or department: the intranet's content slot, the variant this user's profile admits. Set per request in
     * {@link #component}, because the welcome composer reads the fields as it composes; null — and no
     * tile — when nothing applies or content-hub does not answer.
     */
    @Panel(title = "Novedades", colSpan = 3)
    Component novedades;

    final List<MetricCard> usage = ApiUsageWidget.kpis("verApisExternas");

    @Panel(title = "")
    MetricCard salesforce = usage.get(0);

    @Panel(title = "")
    MetricCard salesforcePace = usage.get(1);

    @Panel(title = "")
    MetricCard opera = usage.get(2);

    @Override
    public Component component(HttpRequest httpRequest) {
        novedades = ContentSlots.block(httpRequest, SLOT).orElse(null);
        return super.component(httpRequest);
    }

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
