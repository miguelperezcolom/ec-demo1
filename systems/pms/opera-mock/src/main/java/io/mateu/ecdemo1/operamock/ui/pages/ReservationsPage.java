package io.mateu.ecdemo1.operamock.ui.pages;

import io.mateu.ecdemo1.operamock.ui.Paging;
import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.operamock.store.OperaStore;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.fluent.OnLoadTrigger;
import io.mateu.uidl.fluent.Trigger;
import io.mateu.uidl.fluent.TriggersSupplier;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.interfaces.Searchable;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.Comparator;

@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Reservations in Opera")
public class ReservationsPage implements Listing<ReservationRow>, Searchable, TriggersSupplier {

    final OperaStore store;

    @Override
    public ListingData<ReservationRow> search(SearchRequest request, HttpRequest httpRequest) {
        var text = request.searchText() == null ? "" : request.searchText().toLowerCase();
        var rows = store.reservations().stream()
                .map(ReservationsPage::row)
                .filter(r -> (r.id() + " " + r.crsLocator() + " " + r.hotel()).toLowerCase().contains(text))
                .sorted(Comparator.comparing(ReservationRow::id).reversed())
                .map(r -> new ReservationRow(r.id(), r.hotel(), r.crsLocator(), r.arrival(), r.departure(), r.roomType(),
                        r.ratePlan(), r.packageCode(), r.crsVersion(), store.deposits(r.id()).size(), r.status()))
                .toList();
        return Paging.page(rows, request);
    }

    static ReservationRow row(JsonNode r) {
        var stay = r.path("roomStay");
        var rate = stay.path("roomRates").path(0);
        String locator = "";
        for (var ref : r.path("externalReferences")) {
            if (locator.isEmpty()) {
                locator = ref.path("id").asText();
            }
        }
        String version = "";
        for (var udf : r.path("userDefinedFields").path("numericUDFs")) {
            if ("CRS_VERSION".equals(udf.path("name").asText())) {
                version = udf.path("value").asText();
            }
        }
        var cancelled = "Cancelled".equals(r.path("reservationStatus").asText());
        return new ReservationRow(r.path("reservationIdList").path(0).path("id").asText(), r.path("hotelId").asText(),
                locator, stay.path("arrivalDate").asText(), stay.path("departureDate").asText(),
                rate.path("roomType").asText(), rate.path("ratePlanCode").asText(),
                r.path("reservationPackages").path(0).path("packageCode").asText(), version, 0,
                cancelled ? new Status(StatusType.DANGER, "Cancelled") : new Status(StatusType.SUCCESS, "Reserved"));
    }

    /**
     * Search as soon as the page loads. A listing that is not navigable does not do it by itself —
     * it stays on its loading skeleton until someone types in the search box.
     */
    @Override
    public java.util.List<Trigger> triggers(HttpRequest httpRequest) {
        return java.util.List.of(new OnLoadTrigger("search"));
    }
}
