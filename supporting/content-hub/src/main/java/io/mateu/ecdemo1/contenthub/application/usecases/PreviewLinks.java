package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.PreviewLinkRepository;
import io.mateu.ecdemo1.contenthub.application.out.PreviewLinkUrls;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.preview.PreviewLink;
import io.mateu.ecdemo1.contenthub.domain.preview.PreviewLinkStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Reviewer preview links ({@link PreviewLink}). Whoever holds an editorial permission makes and revokes
 * them — sharing a draft for review is part of writing it, and a publisher reviews too. Whoever is logged
 * in to the intranet opens them: the link is the permission to see that one draft, read-only, nothing
 * else; without a login there is no reader, and nothing is shown.
 */
@Service
@RequiredArgsConstructor
public class PreviewLinks {

    final PreviewLinkRepository links;
    final ItemRepository items;
    final PreviewLinkUrls urls;
    final Clock clock;

    /** What opening a link gives: the link and its item, and whether it may be shown ({@code ACTIVE}). */
    public record Opened(PreviewLink link, Item item, PreviewLinkStatus status) {
        public boolean shown() {
            return status == PreviewLinkStatus.ACTIVE;
        }
    }

    @Transactional
    public PreviewLink create(Reader reader, String itemId, int days, String viewAs, String viewAsLabel) {
        requireEditor(reader);
        var item = items.find(itemId).orElseThrow(() -> new IllegalArgumentException("No existe el contenido " + itemId));
        var link = PreviewLink.create(UUID.randomUUID().toString().replace("-", ""), item, reader.userId(),
                Duration.ofDays(days <= 0 ? PreviewLink.DEFAULT_VALIDITY.toDays() : days), viewAs, viewAsLabel, clock.instant());
        links.save(link);
        return link;
    }

    /**
     * The link behind the visual editor's «Vista previa», for its editor: the one they already have for this item
     * and profile if it's still good, so previewing again doesn't pile up links; or a new one, for a day.
     */
    @Transactional
    public PreviewLink forEditor(Reader reader, String itemId, String viewAs, String viewAsLabel) {
        requireEditor(reader);
        return links.ofItem(itemId).stream()
                .filter(l -> reader.userId().equals(l.createdBy()))
                .filter(l -> java.util.Objects.equals(viewAs, l.viewAs()))
                .filter(l -> status(l) == io.mateu.ecdemo1.contenthub.domain.preview.PreviewLinkStatus.ACTIVE)
                .findFirst()
                .orElseGet(() -> create(reader, itemId, 1, viewAs, viewAsLabel));
    }

    @Transactional
    public void revoke(Reader reader, String linkId) {
        requireEditor(reader);
        var link = links.find(linkId).orElseThrow(() -> new IllegalArgumentException("No existe el enlace " + linkId));
        link.revoke(reader.userId(), clock.instant());
        links.save(link);
    }

    public List<PreviewLink> ofItem(String itemId) {
        return links.ofItem(itemId);
    }

    public List<PreviewLink> all() {
        return links.findAll();
    }

    public PreviewLinkStatus status(PreviewLink link) {
        return link.status(items.find(link.itemId()).orElse(null), clock.instant());
    }

    public String url(PreviewLink link) {
        return urls.url(link);
    }

    /**
     * A reviewer opens a link: refused without a login; a token that is not signed here, or names no link,
     * shows nothing ({@code null}); an expired, revoked or superseded one says why. A view is recorded only
     * when the draft is shown, and only when {@code countView} (a page's redraw is not another visit).
     */
    @Transactional
    public Opened open(Reader viewer, String token, boolean countView) {
        if (viewer == null || viewer.userId() == null || viewer.userId().isBlank() || "anonymous".equals(viewer.userId())) {
            throw new SecurityException("Inicia sesión en la intranet para ver esta vista previa");
        }
        var link = links.find(PreviewLinkUrls.idOf(token)).orElse(null);
        if (link == null || !urls.valid(link, token)) {
            return null;
        }
        var item = items.find(link.itemId()).orElse(null);
        var status = link.status(item, clock.instant());
        if (status == PreviewLinkStatus.ACTIVE && countView) {
            link.recordView(viewer.userId(), clock.instant());
            links.save(link);
        }
        return new Opened(link, item, status);
    }

    static void requireEditor(Reader reader) {
        if (reader.permissions().isEmpty()) {
            throw new SecurityException("Los enlaces de vista previa los crea y revoca quien tiene un permiso editorial "
                    + "(content:edit, content:publish o content:admin)");
        }
    }
}
