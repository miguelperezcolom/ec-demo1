package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.health.ContentHealth;
import io.mateu.ecdemo1.contenthub.domain.health.HealthCheck;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ListToolbarButton;
import io.mateu.uidl.annotations.PrimaryColumn;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.Trigger;
import io.mateu.uidl.annotations.TriggerType;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.interfaces.Filterable;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.interfaces.Navigable;
import io.mateu.uidl.interfaces.Searchable;
import io.mateu.uidl.interfaces.SubtitleSupplier;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * «Salud del contenido»: what the checks found — broken links, orphan media, images without a text
 * alternative, what expires or expired and is still linked, what nobody updates or opens — with the counts per
 * check in the subtitle. Opening a finding gives its fix. Run every night; «Comprobar ahora» runs it now.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Salud del contenido")
@Editors
@Trigger(type = TriggerType.OnCustomEvent, actionId = "search", eventName = HealthListing.CHANGED)
public class HealthListing implements Listing<HealthListing.Row>, Navigable<FindingPage, String>, Searchable,
        Filterable<HealthListing.Filters>, SubtitleSupplier {

    static final String CHANGED = "content-health-changed";

    /** The checks, as the filter offers them (the domain's enum carries no UI labels). */
    public enum Comprobacion {
        @Label("Enlaces rotos") BROKEN_LINKS,
        @Label("Medios huérfanos") ORPHAN_MEDIA,
        @Label("Imágenes sin texto alternativo") MISSING_ALT,
        @Label("Caducidad") EXPIRY,
        @Label("Contenido desactualizado") STALE
    }

    public enum Estado {
        @Label("Pendientes") PENDIENTE,
        @Label("Revisados") REVISADO
    }

    public static class Filters {
        @Label("Comprobación")
        Set<Comprobacion> comprobacion;
        @Label("Estado")
        Set<Estado> estado;
    }

    public record Row(@Hidden String id, @Label("Problema") @PrimaryColumn(caption = "subject") String problem,
                      @Hidden @Label("Dónde") String subject, @Label("Comprobación") Status check,
                      @Label("Desde") String since, @Label("Estado") Status state) {
    }

    final ContentHealth health;
    final Readers readers;
    final ObjectProvider<FindingPage> pages;

    @Override
    public ListingData<Row> search(SearchRequest request, HttpRequest httpRequest) {
        var filters = request == null ? null : filters(request);
        var checks = filters == null || filters.comprobacion == null || filters.comprobacion.isEmpty() ? null
                : filters.comprobacion.stream().map(c -> HealthCheck.valueOf(c.name())).collect(Collectors.toSet());
        // pending by default: what is marked as reviewed is shown only when asked for
        var states = filters == null || filters.estado == null || filters.estado.isEmpty() ? Set.of(Estado.PENDIENTE)
                : filters.estado;
        var text = request == null || request.searchText() == null ? "" : request.searchText().trim().toLowerCase(Locale.ROOT);
        var rows = health.views().stream()
                .filter(v -> checks == null || checks.contains(v.finding().check()))
                .filter(v -> states.contains(v.reviewed() ? Estado.REVISADO : Estado.PENDIENTE))
                .filter(v -> text.isEmpty() || (v.finding().problem() + " " + v.finding().subjectTitle()).toLowerCase(Locale.ROOT).contains(text))
                .sorted(Comparator.comparing((ContentHealth.View v) -> v.finding().check().ordinal())
                        .thenComparing(v -> v.finding().subjectTitle(), Comparator.nullsLast(Comparator.naturalOrder())))
                .map(v -> {
                    var f = v.finding();
                    return new Row(f.key(), f.problem(), f.subject().label() + ": " + f.subjectTitle(),
                            new Status(color(f.check()), f.check().label()), Ui.day(v.stored().firstSeen()),
                            v.reviewed() ? new Status(StatusType.NONE, "Revisado hasta el " + Ui.day(v.snooze().until()))
                                    : new Status(StatusType.WARNING, "Pendiente"));
                }).toList();
        var empty = health.summary().lastRun().isEmpty() ? "Aún no se ha comprobado nada: pulsa «Comprobar ahora»"
                : "Nada que revisar con estos filtros";
        return new ListingData<>(new Page<>(text, Math.max(20, rows.size()), 0, rows.size(), rows), empty);
    }

    static StatusType color(HealthCheck check) {
        return switch (check) {
            case BROKEN_LINKS -> StatusType.DANGER;
            case EXPIRY, MISSING_ALT -> StatusType.WARNING;
            case ORPHAN_MEDIA, STALE -> StatusType.INFO;
        };
    }

    /** The counts per check (pending; reviewed ones apart) and when it last ran. */
    @Override
    public String subtitle() {
        var s = health.summary();
        var counts = java.util.Arrays.stream(HealthCheck.values())
                .map(c -> c.label() + ": " + s.open().get(c)).collect(Collectors.joining(" · "));
        var reviewed = s.reviewed().values().stream().mapToInt(Integer::intValue).sum();
        var last = s.lastRun().map(r -> r.finishedAt() == null ? "comprobando desde las " + Ui.when(r.startedAt())
                : "última comprobación: " + Ui.when(r.finishedAt()) + (r.error() == null ? "" : " (falló: " + r.error() + ")"))
                .orElse("nunca comprobado");
        return counts + (reviewed > 0 ? " · revisados: " + reviewed : "") + " — " + last
                + (s.running() ? " · en marcha" : "");
    }

    @Override
    public FindingPage view(String id, HttpRequest httpRequest) {
        return pages.getObject().load(id, httpRequest);
    }

    /** Runs the checks now, in the background; the list is searched again when asked. */
    @ListToolbarButton(rowsSelectedRequired = false)
    @Label("Comprobar ahora")
    public void comprobarAhora() {
        // handled in handleAction, like every action of this listing
    }

    /** Marks the selected findings as reviewed for 30 days. */
    @ListToolbarButton
    @Label("Marcar como revisados (30 días)")
    public void marcarComoRevisados() {
        // handled in handleAction
    }

    @Override
    public boolean supportsAction(String actionId) {
        return "comprobarAhora".equals(actionId) || "marcarComoRevisados".equals(actionId)
                || Listing.super.supportsAction(actionId);
    }

    @Override
    public Object handleAction(String actionId, HttpRequest httpRequest) {
        if ("comprobarAhora".equals(actionId)) {
            var started = health.startNow(readers.of(httpRequest));
            return List.of(started ? Message.success("Comprobando: los enlaces externos tardan un poco. Vuelve a buscar en unos segundos.")
                    : Message.warning("Ya hay una comprobación en marcha"), UICommand.dispatchEvent(CHANGED));
        }
        if ("marcarComoRevisados".equals(actionId)) {
            var reader = readers.of(httpRequest);
            var ids = httpRequest.getListOfMaps("crud_selected_items").stream()
                    .map(row -> row.get("id")).filter(Objects::nonNull).map(String::valueOf).toList();
            if (ids.isEmpty()) {
                return Message.warning("Selecciona primero los avisos");
            }
            ids.forEach(id -> health.snooze(reader, id, 30, "marcado desde la lista"));
            return List.of(Message.success(ids.size() + (ids.size() == 1 ? " aviso marcado" : " avisos marcados")
                    + " como revisado durante 30 días"), UICommand.dispatchEvent(CHANGED));
        }
        return Listing.super.handleAction(actionId, httpRequest);
    }
}
