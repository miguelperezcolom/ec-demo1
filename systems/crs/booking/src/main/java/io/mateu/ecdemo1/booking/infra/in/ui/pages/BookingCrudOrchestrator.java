package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.uidl.annotations.Action;
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
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.ecdemo1.uicommons.paging.DbPaging;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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
    final DemoBookingsForm demoBookingsForm;
    final BookingQueryService queryService;
    final CrsCatalog catalog;

    @Override
    public ListingData<BookingRow> search(SearchRequest request, HttpRequest httpRequest) {
        return DbPaging.page(request, p -> queryService.findAll(request.searchText(), criteria(filters(request)),
                        PageRequest.of(p.getPageNumber(), p.getPageSize(),
                                DbPaging.pageable(request.pageable(), SORTABLE).getSort())),
                booking -> BookingRow.of(booking, this::hotelName));
    }

    /** Grid column → booking property: what a click on a column's header sorts by. */
    static final Map<String, String> SORTABLE = Map.of("id", "id", "hotel", "hotelCode", "holder", "holderName",
            "arrival", "arrival", "departure", "departure", "status", "status");

    /** A person reads a hotel by its name; the code stays for a hotel the catalog no longer has. */
    String hotelName(String code) {
        return catalog.hotels().stream().filter(h -> h.code().equals(code)).map(CrsCatalog.Hotel::name)
                .findFirst().orElse(code);
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

    /**
     * Ten plausible bookings for MRU01, made through the same use cases as one made by hand. Needs no
     * selection; the dialog it opens asks before creating anything, since MRU01 may write to Opera.
     */
    @ListToolbarButton(rowsSelectedRequired = false, confirmationRequired = true)
    @Action(confirmationTitle = "+ 10 reservas demo",
            confirmationMessage = "Se crearán 10 reservas de MRU01 con datos aleatorios, con llegada en las próximas"
                    + " 2 a 8 semanas, algunas de turoperadores y agencias online. " + DemoBookingsForm.OPERA_WARNING,
            confirmationText = "Crear", confirmationDenialText = "Cancelar")
    @Label("+ 10 reservas demo")
    public Object seedDemo() {
        return List.of(demoBookingsForm.create().message(), UICommand.navigateTo(LIST_ROUTE));
    }

    @Override
    public String getIdFieldForRow() {
        return "id";
    }
}
