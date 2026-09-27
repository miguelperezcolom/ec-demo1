package io.mateu.ecdemo1.journey.ui;

import io.mateu.ecdemo1.journey.JourneyProperties;
import io.mateu.ecdemo1.journey.application.Journeys;
import io.mateu.ecdemo1.journey.model.Durations;
import io.mateu.ecdemo1.uicommons.paging.Paging;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.interfaces.Navigable;
import io.mateu.uidl.interfaces.Searchable;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * The bookings that changed lately, newest first — found in Tempo by the locator their spans carry —
 * and the free text narrows by locator. A row opens the booking's journey.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Recorrido de las reservas")
public class JourneyListPage implements Listing<JourneyRow>, Searchable, Navigable<JourneyView, String> {

    final Journeys journeys;
    final JourneyProperties properties;
    final ObjectProvider<JourneyView> view;

    @Override
    public ListingData<JourneyRow> search(SearchRequest request, HttpRequest httpRequest) {
        var when = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of(properties.zone()));
        var rows = journeys.recent(request == null ? null : request.searchText()).stream()
                .map(b -> new JourneyRow(b.locator(), b.hotel(), when.format(b.lastChange()),
                        new Status(tone(b.lastChangeKind()), b.lastChangeKind()), b.changes(),
                        Durations.words(Duration.ofMillis(b.lastDurationMillis()))))
                .toList();
        var page = Paging.page(rows, request);
        return new ListingData<>(page.page(), "Ninguna reserva con cambios en los últimos días coincide");
    }

    static StatusType tone(String kind) {
        return switch (kind) {
            case "Cancelada", "No-show" -> StatusType.DANGER;
            case "Creada", "Walk-in" -> StatusType.SUCCESS;
            case "Modificada" -> StatusType.INFO;
            default -> StatusType.NONE;
        };
    }

    /** A locator — or a locator and one of its changes, "36K69K~traceId" — opens the journey. */
    @Override
    public JourneyView view(String id, HttpRequest httpRequest) {
        return view.getObject().load(id);
    }
}
