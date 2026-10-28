package io.mateu.ecdemo1.controlshell.infra.in.ui;

import io.mateu.core.infra.declarative.orchestrators.welcome.Welcome;
import io.mateu.ecdemo1.uicommons.user.ApiUsageWidget;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Panel;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.MetricCard;

import java.net.URI;
import java.util.List;

/**
 * The control plane's home ({@link ControlShellHome#homeRoute()}): the welcome archetype — a hero,
 * and under it the external APIs' tiles: Salesforce's calls left of the org's daily allowance, our
 * pace, and Opera's calls today. Each opens the usage page.
 *
 * <p>A welcome page, and not a landing template with a micro frontend under it, because a welcome's
 * {@code @Panel} tiles are what both renderers draw as KPIs; Redwood draws no micro frontend in a
 * page's body. Built per request from integrations-service's numbers: it costs Salesforce nothing.
 */
@Title("Control plane")
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
        return "Plano de control";
    }

    @Override
    protected String heroSubtitle() {
        return "IA, usuarios, workflows y formularios de la plataforma.";
    }

    /**
     * The band's texture. Vaadin draws it on the right of the band. Redwood's welcome banner is
     * Spectra's, which draws a texture in the band's right half from its top centre, clipped to the
     * band: there it is one shaped as redwood.oracle.com's (864x250, the shapes on the right).
     */
    @Override
    protected String heroImage() {
        return REDWOOD ? "/images/redwood-banner-texture.png" : "/images/redwood-header-texture.png";
    }

    /** Whether this build renders with Redwood (mateu-redwood on the classpath), not Vaadin. */
    static final boolean REDWOOD = Inicio.class.getResource("/static/_redwood/bundles/vb-app-bundle.js") != null;

    /** A tile opens the usage page: the breakdown by purpose, ours against others, the pause. */
    @Action
    Object verApisExternas() {
        return URI.create("/usage/apis");
    }

    /** Each console's own colour (its logo's pill), so the home says where one is. */
    @Override
    protected io.mateu.uidl.data.HeroTone heroTone() {
        return io.mateu.uidl.data.HeroTone.plum;
    }
}
