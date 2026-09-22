package io.mateu.ecdemo1.operamock.ui.pages;

import io.mateu.ecdemo1.operamock.store.OperaStore;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Profiles in Opera")
public class ProfilesPage implements Listing<ProfileRow> {

    final OperaStore store;

    @Override
    public ListingData<ProfileRow> search(SearchRequest request, HttpRequest httpRequest) {
        var rows = store.profiles().stream().map(p -> {
            var details = p.path("profileDetails");
            var name = details.path("company").path("companyName").asText(null);
            if (name == null) {
                var person = details.path("customer").path("personName").path(0);
                name = person.path("givenName").asText("") + " " + person.path("surname").asText("");
            }
            return new ProfileRow(p.path("profileIdList").path(0).path("id").asText(), details.path("profileType").asText(),
                    name.trim(), p.path("externalReferences").path(0).path("id").asText());
        }).toList();
        return new ListingData<>(new Page<>(null, rows.size(), 0, rows.size(), rows));
    }
}
