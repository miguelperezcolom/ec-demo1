package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.visibility.ProjectionCache;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.navigation.Section;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** What the portal's screens share: the reader, the visibility function, the section names. */
@Service
@RequiredArgsConstructor
public class PortalSupport {

    final Readers readers;
    final Visibility visibility;
    final NavigationRepository navigation;
    final ProjectionCache cache;

    public Reader reader(HttpRequest request) {
        return readers.of(request);
    }

    public Map<String, String> sectionNames(String language) {
        return navigation.get().sections().stream().collect(Collectors.toMap(Section::id, s -> s.name(language)));
    }

    /** One page of a query, through the visibility function, as Mateu's listing wants it. */
    public ListingData<ItemRow> listing(HttpRequest httpRequest, SearchRequest request, ContentQuery query,
                                        String empty) {
        var reader = reader(httpRequest);
        var pageable = request == null ? null : request.pageable();
        var size = pageable == null || pageable.size() <= 0 ? 20 : pageable.size();
        var number = pageable == null ? 0 : Math.max(0, pageable.page());
        var q = new ContentQuery(query.types(), query.sectionId(), query.itemIds(), query.text(), number * size, size);
        var page = visibility.visibles(reader.attributes(), q);
        return rows(page, reader, number, size, request == null ? "" : request.searchText(), empty);
    }

    public ListingData<ItemRow> rows(ContentIndex.Page<VisibleItem> page, Reader reader, int number, int size,
                                     String signature, String empty) {
        var names = sectionNames(reader.attributes().language());
        var rows = page.content().stream().map(i -> row(i, names)).toList();
        return new ListingData<>(new Page<>(signature == null ? "" : signature, size, number, page.total(), rows), empty);
    }

    public static ItemRow row(VisibleItem i, Map<String, String> sectionNames) {
        return new ItemRow(i.itemId(), i.title(), ItemType.valueOf(i.type()).label(),
                i.sectionId() == null ? "" : sectionNames.getOrDefault(i.sectionId(), i.sectionId()),
                Ui.day(i.publishAt()), i.summary());
    }

    public Optional<VisibleItem> item(Reader reader, String id) {
        return visibility.item(reader.attributes(), id);
    }

    public List<VisibleItem> latest(Reader reader, ContentQuery query) {
        return visibility.visibles(reader.attributes(), query).content();
    }
}
