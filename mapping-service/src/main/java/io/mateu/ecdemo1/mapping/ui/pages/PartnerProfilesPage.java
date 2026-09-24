package io.mateu.ecdemo1.mapping.ui.pages;

import io.mateu.ecdemo1.mapping.ui.Paging;
import io.mateu.ecdemo1.mapping.store.PartnerProfileRepository;
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

/** Which PMS profile each partner of the CRS is, and the partner version last projected to it. */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Partners in the PMS")
public class PartnerProfilesPage implements Listing<PartnerProfileRow>, io.mateu.uidl.interfaces.Searchable, TriggersSupplier {

    final PartnerProfileRepository profiles;

    @Override
    public ListingData<PartnerProfileRow> search(SearchRequest request, HttpRequest httpRequest) {
        var text = request.searchText() == null ? "" : request.searchText().trim().toLowerCase();
        var rows = profiles.findAll().stream()
                .filter(p -> text.isEmpty() || (p.partnerCode + " " + p.profileType + " " + p.pmsProfileId).toLowerCase().contains(text))
                .sorted(java.util.Comparator.comparing(p -> String.valueOf(p.partnerCode)))
                .map(p -> new PartnerProfileRow(p.partnerCode, p.profileType, p.pmsProfileId, p.projectedVersion,
                        String.valueOf(p.updatedAt)))
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
