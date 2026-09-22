package io.mateu.ecdemo1.mapping.ui.pages;

import io.mateu.ecdemo1.mapping.store.PartnerProfileRepository;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/** Which PMS profile each partner of the CRS is, and the partner version last projected to it. */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Partners in the PMS")
public class PartnerProfilesPage implements Listing<PartnerProfileRow> {

    final PartnerProfileRepository profiles;

    @Override
    public ListingData<PartnerProfileRow> search(SearchRequest request, HttpRequest httpRequest) {
        var rows = profiles.findAll().stream()
                .map(p -> new PartnerProfileRow(p.partnerCode, p.profileType, p.pmsProfileId, p.projectedVersion,
                        String.valueOf(p.updatedAt)))
                .toList();
        return new ListingData<>(new Page<>(null, rows.size(), 0, rows.size(), rows));
    }
}
