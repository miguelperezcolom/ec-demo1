package io.mateu.ecdemo1.contenthub.application.visibility;

import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * THE visibility function: everything that enumerates content — pages, blocks, menus, listings, search,
 * slots, newsletter, MCP — asks here, and none filters on its own. Pure in its inputs: the reader's
 * attributes, the instant and the query.
 *
 * <p>It never evaluates an expression and never filters in memory: it makes sure the reader's profile is
 * in the projection (materialising it synchronously the first time) and lets the store filter by
 * {@code hash ∈ hashes(subject) ∨ TODOS ∈ hashes(subject)} plus the dates.
 */
@Service
@RequiredArgsConstructor
public class Visibility {

    final ContentIndex index;
    final Projection projection;
    final Clock clock;

    public ContentIndex.Page<VisibleItem> visibles(EmployeeAttributes reader, Instant now, ContentQuery query) {
        ready(reader);
        return index.visibleItems(reader.hash(), reader.language(), now, query);
    }

    public ContentIndex.Page<VisibleItem> visibles(EmployeeAttributes reader, ContentQuery query) {
        return visibles(reader, clock.instant(), query);
    }

    public boolean isVisible(EmployeeAttributes reader, Instant now, String itemId) {
        ready(reader);
        return index.isVisible(itemId, reader.hash(), now);
    }

    public boolean isVisible(EmployeeAttributes reader, String itemId) {
        return isVisible(reader, clock.instant(), itemId);
    }

    /** One item, in the reader's language, if they may see it. */
    public Optional<VisibleItem> item(EmployeeAttributes reader, String itemId) {
        return visibles(reader, ContentQuery.ids(List.of(itemId))).content().stream().findFirst();
    }

    /** The intranet's sections with something the reader sees. */
    public Set<String> sectionsWithVisibleContent(EmployeeAttributes reader) {
        return sectionsWithVisibleContent(reader, io.mateu.ecdemo1.contenthub.domain.site.Site.INTRANET);
    }

    /** A site's sections with something the reader sees. */
    public Set<String> sectionsWithVisibleContent(EmployeeAttributes reader, String siteId) {
        ready(reader);
        return index.sectionsWithVisibleContent(reader.hash(), clock.instant(), siteId);
    }

    /** The page's blocks the reader sees: page AND block, as the projection holds it. */
    public List<String> visibleBlockIds(EmployeeAttributes reader, String pageId) {
        ready(reader);
        return index.visibleBlockIds(pageId, reader.hash());
    }

    public Optional<ContentIndex.VisibleVariant> slot(EmployeeAttributes reader, String slotKey) {
        ready(reader);
        return index.topVisibleVariant(slotKey, reader.hash());
    }

    /** A media file is served only to someone who can see a published item that uses it. */
    public boolean isMediaVisible(EmployeeAttributes reader, String mediaId) {
        ready(reader);
        return index.isMediaVisible(mediaId, reader.hash(), clock.instant());
    }

    /** The surveys open now that the reader's audience sees. */
    public List<ContentIndex.VisibleSurvey> openSurveys(EmployeeAttributes reader) {
        ready(reader);
        return index.openSurveys(reader.hash(), clock.instant(), null);
    }

    /** One survey, if it is open now and for the reader. */
    public Optional<ContentIndex.VisibleSurvey> openSurvey(EmployeeAttributes reader, String surveyId) {
        ready(reader);
        return index.openSurveys(reader.hash(), clock.instant(), surveyId).stream().findFirst();
    }

    /** The Google Drive files the items the reader sees point at (their URL, or a Drive medium they use). */
    public List<ContentIndex.ExternalReference> googleUrls(EmployeeAttributes reader) {
        ready(reader);
        return index.visibleGoogleUrls(reader.hash(), reader.language(), clock.instant());
    }

    /**
     * The passages most like a question (its vector), among what the reader may see — what the search and
     * the AI answer are built from; the filter is inside the same query.
     */
    public List<ContentIndex.Passage> similarPassages(EmployeeAttributes reader, float[] question, int limit) {
        ready(reader);
        return index.similarVisiblePassages(reader.hash(), reader.language(), clock.instant(), question, limit);
    }

    private void ready(EmployeeAttributes reader) {
        if (!index.knowsProfile(reader.hash())) {
            projection.materialise(reader);
        }
    }
}
