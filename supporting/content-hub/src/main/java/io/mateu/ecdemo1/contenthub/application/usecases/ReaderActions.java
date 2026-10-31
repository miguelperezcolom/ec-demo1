package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.out.AcknowledgementRepository;
import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.out.FavoritesRepository;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.ack.Acknowledgement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * What a reader does besides reading: favourites, read receipts and confirmations. Every one of them
 * checks first that the reader may see the item — through the visibility function.
 */
@Service
@RequiredArgsConstructor
public class ReaderActions {

    final Visibility visibility;
    final FavoritesRepository favorites;
    final AcknowledgementRepository acks;
    final ItemRepository items;
    final Clock clock;

    @Transactional
    public boolean toggleFavorite(Reader reader, String itemId) {
        requireVisible(reader, itemId);
        var fav = favorites.get(reader.userId());
        var now = !fav.contains(itemId);
        if (now) {
            fav.add(itemId);
        } else {
            fav.remove(itemId);
        }
        favorites.save(fav);
        return now;
    }

    public boolean isFavorite(Reader reader, String itemId) {
        return favorites.get(reader.userId()).contains(itemId);
    }

    /** "Mis favoritos": the reader's favourites crossed with the visibility function, in the store. */
    public ContentIndex.Page<VisibleItem> favorites(Reader reader) {
        var ids = favorites.get(reader.userId()).itemIds();
        if (ids.isEmpty()) {
            return new ContentIndex.Page<>(List.of(), 0);
        }
        var page = visibility.visibles(reader.attributes(), ContentQuery.ids(ids));
        // in the order they were starred, most recent first
        var ordered = page.content().stream().sorted(java.util.Comparator.comparingInt(i -> ids.indexOf(i.itemId())))
                .toList();
        return new ContentIndex.Page<>(ordered, page.total());
    }

    /** Recorded only for items with read tracking, and only by a real reader (never "ver como"). */
    @Transactional
    public void recordRead(Reader reader, VisibleItem item) {
        if (reader.viewingAs() != null || !item.readTracking()) {
            return;
        }
        acks.record(new Acknowledgement(item.itemId(), item.version(), reader.userId(), Acknowledgement.Type.READ,
                clock.instant()));
    }

    @Transactional
    public void confirm(Reader reader, String itemId) {
        if (reader.viewingAs() != null) {
            throw new IllegalStateException("En «ver como» no se confirma");
        }
        var item = visibility.item(reader.attributes(), itemId)
                .orElseThrow(() -> new SecurityException("No existe o no lo puedes ver"));
        if (!item.confirmationRequired()) {
            throw new IllegalStateException("Este item no pide confirmación");
        }
        acks.record(new Acknowledgement(itemId, item.version(), reader.userId(), Acknowledgement.Type.CONFIRMATION,
                clock.instant()));
    }

    public boolean hasConfirmed(Reader reader, VisibleItem item) {
        return acks.has(reader.userId(), item.itemId(), Acknowledgement.Type.CONFIRMATION,
                Math.max(1, item.confirmationVersion()));
    }

    /** Visible items with mandatory confirmation the reader has not confirmed in their current version. */
    public List<VisibleItem> pendingConfirmations(Reader reader) {
        return visibility.visibles(reader.attributes(), new ContentQuery(java.util.Set.of(), null, null, null, 0, 500))
                .content().stream()
                .filter(VisibleItem::confirmationRequired)
                .filter(i -> !hasConfirmed(reader, i))
                .toList();
    }

    private void requireVisible(Reader reader, String itemId) {
        if (!visibility.isVisible(reader.attributes(), itemId)) {
            throw new SecurityException("No existe o no lo puedes ver");
        }
    }
}
