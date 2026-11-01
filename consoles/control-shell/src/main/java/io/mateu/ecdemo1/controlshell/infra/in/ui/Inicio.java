package io.mateu.ecdemo1.controlshell.infra.in.ui;

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

    /** The content slot this home shows (content-hub; editors fill it from "Editorial"). */
    static final String SLOT = "control-plane.inicio.avisos";

    /**
     * What the editors publish for the platform's administrators — maintenance windows, changes: the intranet's content slot, the variant this user's profile admits. Set per request in
     * {@link #component}, because the welcome composer reads the fields as it composes; null — and no
     * tile — when nothing applies or content-hub does not answer.
     */
    @Panel(title = "Avisos de plataforma", colSpan = 3)
    Component avisos;

    final List<MetricCard> usage = ApiUsageWidget.kpis("verApisExternas");

    @Panel(title = "")
    MetricCard salesforce = usage.get(0);

    @Panel(title = "")
    MetricCard salesforcePace = usage.get(1);

    @Panel(title = "")
    MetricCard opera = usage.get(2);

    @Override
    public Component component(HttpRequest httpRequest) {
        avisos = ContentSlots.block(httpRequest, SLOT).orElse(null);
        return super.component(httpRequest);
    }

    @Override
    protected String heroTitle() {
        return "Plano de control";
    }

    @Override
    protected String heroSubtitle() {
        return "IA, usuarios, workflows y formularios de la plataforma.";
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
