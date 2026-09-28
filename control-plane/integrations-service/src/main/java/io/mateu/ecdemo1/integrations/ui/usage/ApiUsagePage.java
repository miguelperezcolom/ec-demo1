package io.mateu.ecdemo1.integrations.ui.usage;

import io.mateu.ecdemo1.integration.model.usage.ApiUsage;
import io.mateu.ecdemo1.integrations.usage.ApiUsages;
import io.mateu.ecdemo1.integrations.usage.UsageLevel;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.Meter;
import io.mateu.uidl.data.MetricCard;
import io.mateu.uidl.data.Notice;
import io.mateu.uidl.data.Scoreboard;
import io.mateu.uidl.data.State;
import io.mateu.uidl.data.VerticalLayout;
import io.mateu.uidl.fluent.Component;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * How much of Salesforce and Opera the platform spends — the page behind the header widget.
 *
 * <p>Salesforce: the org's calls in the rolling 24 hours against its daily allowance (15,000 on a
 * Base Edition, for everyone using the org together), how many of them were the MDM's and on what,
 * and the rest — scripts, another environment on the same org, people. That split is what says whose
 * the problem is when the allowance runs out. Opera: our calls to OHIP by module and endpoint, and
 * anything it said of its rate.
 */
@Title("APIs externas")
@Service
@Scope("prototype")
public class ApiUsagePage {

    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.of("Europe/Madrid"));

    final ApiUsages usages;

    public ApiUsagePage(ApiUsages usages) {
        this.usages = usages;
    }

    @Section("Salesforce — últimas 24 horas")
    @Label("")
    Callable<Component> salesforce = () -> salesforce(usages().salesforce().orElse(null));

    @Section("Opera (OHIP) — últimas 24 horas")
    @Label("")
    Callable<Component> opera = () -> opera(usages().opera().orElse(null));

    // One block of markup for both breakdowns, in a section of its own: with a block per API, Mateu
    // 3.0-alpha.373 drew the second one in the first one's section.
    @Section("Nuestras llamadas, desglosadas")
    @Label("")
    Callable<Component> breakdown = () -> block("breakdown",
            salesforceBreakdown(usages().salesforce().orElse(null)) + operaBreakdown(usages().opera().orElse(null)));

    /** A method: the panels' lambdas are built before the constructor runs. */
    ApiUsages usages() {
        return usages;
    }

    @Toolbar
    @Action(idempotent = true)
    @Label("Actualizar")
    public Object refresh() {
        return new State(this);
    }

    static Component salesforce(ApiUsage u) {
        if (u == null) {
            return unknown("El maestro de clientes (customer-mdm), que es quien llama a Salesforce, no ha contestado.");
        }
        var content = new ArrayList<Component>();
        if (u.paused()) {
            content.add(Notice.builder().theme("error")
                    .text("Salesforce ha agotado la cuota diaria de la org: el maestro de clientes retiene sus llamadas"
                            + (u.pausedUntil() == null ? "" : " hasta las " + TIME.format(u.pausedUntil()))
                            + ". No se pierde nada: todo sale cuando la cuota vuelve.")
                    .build());
        }
        if (u.orgUsed() != null && u.orgMax() != null) {
            content.add(Meter.builder()
                    .label("CUOTA DIARIA DE LA ORG")
                    .value((double) u.orgUsed())
                    .max((double) u.orgMax())
                    .caption(percent(u.orgUsed(), u.orgMax()) + " usado · visto " + (u.seenAt() == null ? "—" : TIME.format(u.seenAt())))
                    .warnAt(u.orgMax() * 0.80)
                    .dangerAt(u.orgMax() * 0.95)
                    .build());
        }
        content.add(Scoreboard.builder().id("salesforce-figures").style("width: 100%;").metrics(List.of(
                card("sf-left", "LIBRES", u.orgLeft() == null ? "—" : grouped(u.orgLeft()),
                        u.orgMax() == null ? "Salesforce aún no lo ha dicho" : "de " + grouped(u.orgMax())),
                card("sf-org", "USADAS (TODOS)", u.orgUsed() == null ? "—" : grouped(u.orgUsed()),
                        u.orgUsed1hAgo() == null ? "la org entera" : "hace una hora: " + grouped(u.orgUsed1hAgo())),
                card("sf-ours", "NUESTRAS", grouped(u.ours24h()), "última hora: " + grouped(u.ours1h())
                        + " (la anterior: " + grouped(u.oursPrevious1h()) + ")"),
                card("sf-others", "OTROS", u.others24h() == null ? "—" : grouped(u.others24h()),
                        "scripts, otros entornos, personas"),
                card("sf-limited", "RECHAZADAS POR CUOTA", grouped(u.limited24h()),
                        u.paused() ? "en pausa" : UsageLevel.of(u) == UsageLevel.OK ? "sin pausa" : "cerca del límite")))
                .build());
        return VerticalLayout.builder().id("salesforce-usage").content(content).build();
    }

    static String salesforceBreakdown(ApiUsage u) {
        return u == null ? "" : Html.heading("Salesforce, por propósito") + breakdown(u.byPurpose(), "Propósito")
                + Html.muted("«token» son los tokens OAuth: se cuentan aquí, pero Salesforce no los descuenta de la cuota.");
    }

    static Component opera(ApiUsage u) {
        if (u == null) {
            return unknown("El conector con Opera (pms-integration), que es quien llama a OHIP, no ha contestado.");
        }
        var content = new ArrayList<Component>();
        if (u.limited24h() > 0) {
            content.add(Notice.builder().theme("warning")
                    .text("OHIP ha contestado 429 (demasiadas peticiones) " + u.limited24h()
                            + " veces: el conector reintenta, pero va más rápido de lo que Opera admite.")
                    .build());
        }
        content.add(Scoreboard.builder().id("opera-figures").style("width: 100%;").metrics(List.of(
                card("opera-calls", "LLAMADAS", grouped(u.ours24h()), u.service()),
                card("opera-429", "429", grouped(u.limited24h()), "rechazadas por ritmo"),
                card("opera-errors", "ERRORES", grouped(u.errors24h()), "4xx/5xx y sin respuesta")))
                .build());
        return VerticalLayout.builder().id("opera-usage").content(content).build();
    }

    static String operaBreakdown(ApiUsage u) {
        if (u == null) {
            return "";
        }
        var markup = new StringBuilder();
        markup.append(Html.heading("Opera, por módulo de OHIP")).append(breakdown(u.byPurpose(), "Módulo"));
        markup.append(Html.heading("Opera, por endpoint")).append(breakdown(u.byEndpoint(), "Endpoint"));
        markup.append(Html.heading("Límite de ritmo de OHIP"));
        markup.append(u.rateLimit().isEmpty()
                ? Html.muted("OHIP no ha dicho nada de su límite (ninguna cabecera X-RateLimit-* ni Retry-After).")
                : Html.table(List.of("Cabecera", "Valor"), u.rateLimit().entrySet().stream()
                        .map(e -> List.of(Html.escape(e.getKey()), Html.escape(e.getValue()))).toList()));
        return markup.toString();
    }

    static String breakdown(Map<String, Long> counts, String what) {
        if (counts.isEmpty()) {
            return Html.muted("Ninguna llamada en las últimas 24 horas.");
        }
        return Html.table(List.of(what, "Llamadas"), counts.entrySet().stream()
                .map(e -> List.of(Html.escape(e.getKey()), grouped(e.getValue()))).toList());
    }

    /** Markup as a full-width block, with an id of its own. */
    static Component block(String id, String markup) {
        return io.mateu.uidl.data.Element.html("div", Map.of("style", "width: 100%;", "id", id), markup);
    }

    static Component unknown(String why) {
        return Notice.builder().theme("contrast").text("Sin datos. " + why).build();
    }

    static MetricCard card(String id, String title, String value, String description) {
        return MetricCard.builder().id(id).title(title).value(value).description(description).build();
    }

    static String percent(long used, long max) {
        return max <= 0 ? "—" : Math.round(used * 100.0 / max) + "%";
    }

    /** 15000 → "15.000", 1234 → "1.234": Spanish grouping, also for four digits (Java's es locale leaves those alone). */
    static String grouped(long n) {
        var symbols = new java.text.DecimalFormatSymbols(java.util.Locale.ROOT);
        symbols.setGroupingSeparator('.');
        symbols.setMinusSign('-');
        return new java.text.DecimalFormat("#,##0", symbols).format(n);
    }
}
