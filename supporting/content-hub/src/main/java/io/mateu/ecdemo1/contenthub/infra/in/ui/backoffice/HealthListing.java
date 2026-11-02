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
        @Label("Sitio")
        String sitio;
    }

    public record Row(@Hidden String id, @Label("Problema") @PrimaryColumn(caption = "subject") String problem,
                      @Hidden @Label("Dónde") String subject, @Label("Sitio") String site, @Label("Comprobación") Status check,
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
        var names = health.siteNames();
        var site = filters == null || filters.sitio == null || filters.sitio.isBlank() ? null
                : filters.sitio.trim().toLowerCase(Locale.ROOT);
        var rows = health.views().stream()
                .filter(v -> site == null || siteLabel(names, v.finding().siteId()).toLowerCase(Locale.ROOT).contains(site)
                        || (v.finding().siteId() != null && v.finding().siteId().toLowerCase(Locale.ROOT).contains(site)))
                .filter(v -> checks == null || checks.contains(v.finding().check()))
                .filter(v -> states.contains(v.reviewed() ? Estado.REVISADO : Estado.PENDIENTE))
                .filter(v -> text.isEmpty() || (v.finding().problem() + " " + v.finding().subjectTitle()).toLowerCase(Locale.ROOT).contains(text))
                .sorted(Comparator.comparing((ContentHealth.View v) -> siteLabel(names, v.finding().siteId()))
                        .thenComparing(v -> v.finding().check().ordinal())
                        .thenComparing(v -> v.finding().subjectTitle(), Comparator.nullsLast(Comparator.naturalOrder())))
                .map(v -> {
                    var f = v.finding();
                    return new Row(f.key(), f.problem(), f.subject().label() + ": " + f.subjectTitle(),
                            siteLabel(names, f.siteId()),
                            new Status(color(f.check()), f.check().label()), Ui.day(v.stored().firstSeen()),
                            v.reviewed() ? new Status(StatusType.NONE, "Revisado hasta el " + Ui.day(v.snooze().until()))
                                    : new Status(StatusType.WARNING, "Pendiente"));
                }).toList();
        var empty = health.summary().lastRun().isEmpty() ? "Aún no se ha comprobado nada: pulsa «Comprobar ahora»"
                : "Nada que revisar con estos filtros";
        return new ListingData<>(new Page<>(text, Math.max(20, rows.size()), 0, rows.size(), rows), empty);
    }

    /** A finding's site, by name; the media library is every site's. */
    static String siteLabel(java.util.Map<String, String> names, String siteId) {
        return siteId == null ? "(todos: biblioteca y slots)" : names.getOrDefault(siteId, siteId);
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

    /**
     * The event the crud Mateu puts around this listing listens to, to search again. A Navigable listing is
     * bridged into a crud (CapabilityCrud): its toolbar buttons arrive as {@code action-on-row-<method>} and
     * the METHOD is invoked — an override of {@code handleAction} is never asked, which is why these methods
     * do the work themselves (with no-op methods the button "worked" and nothing ran).
     */
    static final String CRUD_LISTING_CHANGED = "mateu-crud:listing-changed";

    /** Runs the checks now, in the background; the list searches again (and again when asked). */
    @ListToolbarButton(rowsSelectedRequired = false)
    @Label("Comprobar ahora")
    public Object comprobarAhora(HttpRequest httpRequest) {
        var reader = readers.of(httpRequest);
        if (!reader.may(io.mateu.ecdemo1.contenthub.application.security.EditorialPermission.EDIT)) {
            return Message.warning("Para comprobar hace falta el permiso content:edit");
        }
        var started = health.startNow(reader);
        return List.of(started
                        ? Message.success("Comprobación en marcha: los enlaces externos tardan un poco. Vuelve a buscar en unos segundos.")
                        : Message.warning("Ya hay una comprobación en marcha"),
                UICommand.dispatchEvent(CRUD_LISTING_CHANGED), UICommand.dispatchEvent(CHANGED));
    }

    /** Marks the selected findings as reviewed for 30 days. */
    @ListToolbarButton
    @Label("Marcar como revisados (30 días)")
    @SuppressWarnings("rawtypes") // the selected rows as the client sends them (maps), like Mateu's own delete
    public Object marcarComoRevisados(List<java.util.Map> selection, HttpRequest httpRequest) {
        var reader = readers.of(httpRequest);
        var ids = (selection == null ? List.<java.util.Map>of() : selection).stream().map(row -> row.get("id"))
                .filter(Objects::nonNull).map(String::valueOf).toList();
        if (ids.isEmpty()) {
            return Message.warning("Selecciona primero los avisos");
        }
        ids.forEach(id -> health.snooze(reader, id, 30, "marcado desde la lista"));
        return List.of(Message.success(ids.size() + (ids.size() == 1 ? " aviso marcado" : " avisos marcados")
                        + " como revisado durante 30 días"),
                UICommand.dispatchEvent(CRUD_LISTING_CHANGED), UICommand.dispatchEvent(CHANGED));
    }
}
