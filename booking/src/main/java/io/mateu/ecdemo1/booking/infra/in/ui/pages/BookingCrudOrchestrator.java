package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ListToolbarButton;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;
import io.mateu.ecdemo1.booking.application.out.query.BookingQueryService;
import io.mateu.ecdemo1.booking.application.out.query.dto.BookingCriteria;
import io.mateu.ecdemo1.booking.application.out.query.dto.BookingRow;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Scope("prototype")
@Title("Bookings")
public class BookingCrudOrchestrator extends Crud<
        BookingViewModel,
        BookingViewModel,
        BookingViewModel,
        BookingFilters,
        BookingRow,
        String
        > implements OptionsSupplier {

    /** Where the listing is mounted: Call center (the field in BookingHome) → bookings (BookingMenu). */
    static final String LIST_ROUTE = "/booking/bookings";

    final BookingViewModel viewModel;
    final BookingCancellationForm cancellationForm;
    final BookingQueryService queryService;
    final CrsCatalog catalog;

    @Override
    public ListingData<BookingRow> search(SearchRequest request, HttpRequest httpRequest) {
        return queryService.findAll(request.searchText(), criteria(filters(request)), request.pageable());
    }

    static BookingCriteria criteria(BookingFilters filters) {
        if (filters == null) {
            return null;
        }
        return new BookingCriteria(
                filters.hotel == null || filters.hotel.isBlank() ? null : filters.hotel,
                filters.status,
                filters.arrival != null ? filters.arrival.from() : null,
                filters.arrival != null ? filters.arrival.to() : null,
                filters.departure != null ? filters.departure.from() : null,
                filters.departure != null ? filters.departure.to() : null);
    }

    /** The hotel filter's options: the hotels of the CRS catalog. */
    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        if (!"hotel".equals(fieldName)) {
            return List.of();
        }
        return catalog.hotels().stream()
                .map(h -> new Option(h.code(), h.code() + " — " + h.name()))
                .toList();
    }

    @Override
    public BookingViewModel view(String id, HttpRequest httpRequest) {
        return viewModel.load(queryService.getById(id).orElseThrow());
    }

    @Override
    public BookingViewModel edit(String id, HttpRequest httpRequest) {
        return viewModel.load(queryService.getById(id).orElseThrow());
    }

    @Override
    public BookingViewModel creationForm(HttpRequest httpRequest) {
        return viewModel;
    }

    @Override
    public String save(HttpRequest httpRequest) {
        var editor = httpRequest.getComponentState(BookingViewModel.class);
        editor.save(httpRequest);
        return editor.id();
    }

    @Override
    public String create(HttpRequest httpRequest) {
        var form = httpRequest.getComponentState(BookingViewModel.class);
        return form.create(httpRequest);
    }

    /** A booking is cancelled, never deleted: the cancellation is what reaches Opera and the front office. */
    @Override
    public boolean canDelete() {
        return false;
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        throw new UnsupportedOperationException("A booking is cancelled, not deleted");
    }

    /** New opens the wizard, a step at a time, instead of the booking form. */
    @Override
    public Object handleAction(String actionId, HttpRequest httpRequest) {
        if ("new".equals(actionId)) {
            return UICommand.navigateTo(NewBookingWizard.ROUTE);
        }
        return super.handleAction(actionId, httpRequest);
    }

    /** Cancels the selected bookings, after asking why. */
    @ListToolbarButton
    @Label("Cancel")
    public Object cancel(List<BookingRow> selection, HttpRequest httpRequest) {
        if (selection == null || selection.isEmpty()) {
            return Message.error("Select the bookings to cancel");
        }
        return cancellationForm.dialogFor(selection.stream().map(BookingRow::id).toList(), LIST_ROUTE);
    }

    @Override
    public String getIdFieldForRow() {
        return "id";
    }
}
