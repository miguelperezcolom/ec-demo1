package io.mateu.ecdemo1.operamock.ui.pages;

import io.mateu.ecdemo1.operamock.ui.Paging;
import io.mateu.ecdemo1.operamock.store.OperaStore;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.fluent.OnLoadTrigger;
import io.mateu.uidl.fluent.Trigger;
import io.mateu.uidl.fluent.TriggersSupplier;
import io.mateu.uidl.interfaces.Listing;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/** Every call the adapter made, newest first — the conversation with "Opera", as Opera saw it. */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Calls")
public class CallsPage implements Listing<CallRow>, TriggersSupplier {

    final OperaStore store;

    @Override
    public ListingData<CallRow> search(SearchRequest request, HttpRequest httpRequest) {
        var rows = store.calls().stream()
                .map(c -> new CallRow(c.at().toString(), c.method(), c.path(), c.hotelId(), c.status(), c.millis()))
                .toList();
        return Paging.page(rows, request);
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
