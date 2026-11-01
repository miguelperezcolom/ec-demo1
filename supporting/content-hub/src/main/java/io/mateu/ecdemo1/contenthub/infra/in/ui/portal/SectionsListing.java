package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.visibility.ProjectionCache;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.interfaces.Navigable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The navigation as the reader sees it: a section shows when something in it or below it is visible to
 * them; the empty ones are not there. Cached per profile hash and language.
 */
@Service
@Scope("prototype")
@Title("Secciones")
public class SectionsListing implements Listing<SectionRow>, Navigable<SectionPage, String> {

    final PortalSupport portal;
    final Visibility visibility;
    final NavigationRepository navigation;
    final ProjectionCache cache;
    final ObjectProvider<SectionPage> pages;

    public SectionsListing(PortalSupport portal, Visibility visibility, NavigationRepository navigation,
                           ProjectionCache cache, ObjectProvider<SectionPage> pages) {
        this.portal = portal;
        this.visibility = visibility;
        this.navigation = navigation;
        this.cache = cache;
        this.pages = pages;
    }

    @Override
    public ListingData<SectionRow> search(SearchRequest request, HttpRequest httpRequest) {
        var reader = portal.reader(httpRequest);
        var language = reader.attributes().language();
        List<SectionRow> rows = cache.get(reader.attributes().hash(), language, "menu", () -> {
            var nav = navigation.get();
            var shown = nav.shown(visibility.sectionsWithVisibleContent(reader.attributes()));
            var out = new ArrayList<SectionRow>();
            for (var s : nav.sections()) {
                if (shown.contains(s.id())) {
                    var path = nav.ancestors(s.id()).reversed().stream()
                            .map(a -> nav.find(a).map(x -> x.name(language)).orElse(a)).collect(Collectors.joining(" › "));
                    out.add(new SectionRow(s.id(), s.name(language), path));
                }
            }
            return out;
        });
        return new ListingData<>(new Page<>("", Math.max(20, rows.size()), 0, rows.size(), rows),
                "No hay secciones con contenido para ti");
    }

    @Override
    public SectionPage view(String id, HttpRequest httpRequest) {
        return pages.getObject().load(id, httpRequest);
    }
}
