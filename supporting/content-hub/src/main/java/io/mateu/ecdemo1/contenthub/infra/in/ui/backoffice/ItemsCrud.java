package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.domain.item.ItemStatus;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.NoFilters;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

/** Every item, whatever its state: the editors' list. Items are retired, never deleted. */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Contenidos")
@Editors
public class ItemsCrud extends Crud<ItemForm, ItemForm, ItemForm, NoFilters, ItemRow, String> {

    final ObjectProvider<ItemForm> forms;
    final ItemRepository items;

    @Override
    public ListingData<ItemRow> search(SearchRequest request, HttpRequest httpRequest) {
        var text = request == null ? "" : request.searchText();
        var pageable = request == null ? null : request.pageable();
        var size = pageable == null || pageable.size() <= 0 ? 20 : pageable.size();
        var number = pageable == null ? 0 : Math.max(0, pageable.page());
        var rows = items.search(text, number * size, size).stream()
                .map(s -> new ItemRow(s.id(), s.title(), ItemType.valueOf(s.type()).label(), status(s), s.author(),
                        s.updatedAt()))
                .toList();
        return new ListingData<>(new Page<>(text == null ? "" : text, size, number, items.count(text), rows),
                "Todavía no hay contenidos");
    }

    static Status status(ItemRepository.ItemSummary s) {
        var st = ItemStatus.valueOf(s.status());
        var text = st.label() + (s.inReview() && st != ItemStatus.IN_REVIEW ? " · cambios en revisión"
                : s.pendingChanges() ? " · cambios sin publicar" : "");
        return new Status(switch (st) {
            case DRAFT -> StatusType.NONE;
            case IN_REVIEW -> StatusType.WARNING;
            case PUBLISHED -> StatusType.SUCCESS;
            case RETIRED -> StatusType.DANGER;
        }, text);
    }

    @Override
    public ItemForm view(String id, HttpRequest httpRequest) {
        return forms.getObject().load(items.find(id).orElseThrow());
    }

    @Override
    public ItemForm edit(String id, HttpRequest httpRequest) {
        return view(id, httpRequest);
    }

    @Override
    public ItemForm creationForm(HttpRequest httpRequest) {
        return forms.getObject();
    }

    @Override
    public String save(HttpRequest httpRequest) {
        var form = httpRequest.getComponentState(ItemForm.class);
        form.save(httpRequest);
        return form.id();
    }

    @Override
    public String create(HttpRequest httpRequest) {
        return httpRequest.getComponentState(ItemForm.class).create(httpRequest);
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        throw new IllegalStateException("Los contenidos no se borran: retíralos");
    }

    @Override
    public String getIdFieldForRow() {
        return "id";
    }
}
