package io.mateu.ecdemo1.booking.infra.in.ui.pages.catalog;

import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Call center → Catalogue → Rate plans: the rate plans each hotel sells, and New to open one (demo
 * flow 8, until now only REST's POST /catalog/hotels/{hotel}/rate-plans or ec1.py rate-plan). A rate
 * plan is opened, never edited nor deleted here: bookings and the integration's mapping hang from it.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Rate plans")
public class RatePlansCrud extends Crud<RatePlanForm, RatePlanForm, RatePlanForm, RatePlanFilters, RatePlanRow, String>
        implements OptionsSupplier {

    final CrsCatalog catalog;
    final RatePlanForm form;

    @Override
    public ListingData<RatePlanRow> search(SearchRequest request, HttpRequest httpRequest) {
        var filters = filters(request);
        var rows = rows(filters == null ? null : filters.hotel, request.searchText());
        return new ListingData<>(new Page<>(request.searchText(), rows.size(), 0, rows.size(), rows));
    }

    /** Every hotel's rate plans (or one's), those it has of its own or the chain's, by hotel and code. */
    List<RatePlanRow> rows(String hotel, String searchText) {
        var text = searchText == null ? "" : searchText.toLowerCase(Locale.ROOT).strip();
        return catalog.hotels().stream()
                .filter(h -> hotel == null || hotel.isBlank() || h.code().equals(hotel))
                .flatMap(h -> catalog.codes(h.code()).ratePlans().stream()
                        .map(p -> new RatePlanRow(h.code() + "/" + p.code(), h.code() + " — " + h.name(), p.code(),
                                p.name(), p.factor().stripTrailingZeros().toPlainString(),
                                h.codes() != null ? "Own (imported from its PMS)" : "Chain's")))
                .filter(r -> text.isEmpty() || (r.code() + " " + r.name() + " " + r.hotel())
                        .toLowerCase(Locale.ROOT).contains(text))
                .sorted(Comparator.comparing(RatePlanRow::hotel).thenComparing(RatePlanRow::code))
                .toList();
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        if (!"hotel".equals(fieldName)) {
            return List.of();
        }
        return catalog.hotels().stream().map(h -> new Option(h.code(), h.code() + " — " + h.name())).toList();
    }

    @Override
    public RatePlanForm creationForm(HttpRequest httpRequest) {
        return form;
    }

    @Override
    public String create(HttpRequest httpRequest) {
        return httpRequest.getComponentState(RatePlanForm.class).create(httpRequest);
    }

    @Override
    public boolean canView() {
        return false;
    }

    @Override
    public boolean canEdit() {
        return false;
    }

    @Override
    public boolean canDelete() {
        return false;
    }

    @Override
    public RatePlanForm view(String id, HttpRequest httpRequest) {
        throw new UnsupportedOperationException("A rate plan is listed, not opened");
    }

    @Override
    public RatePlanForm edit(String id, HttpRequest httpRequest) {
        throw new UnsupportedOperationException("A rate plan is opened, not edited");
    }

    @Override
    public String save(HttpRequest httpRequest) {
        throw new UnsupportedOperationException("A rate plan is opened, not edited");
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        throw new UnsupportedOperationException("A rate plan is not deleted: bookings and the mapping use it");
    }

    @Override
    public String getIdFieldForRow() {
        return "id";
    }
}
