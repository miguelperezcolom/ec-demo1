package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.domain.item.Item;

/** An item's title for the editors (its draft, in its default language). */
final class ItemTitles {

    private ItemTitles() {
    }

    static String of(ItemRepository items, String itemId) {
        return items.find(itemId).map(ItemTitles::of).orElse(itemId);
    }

    static String of(Item item) {
        return item.draft().in(item.defaultLanguage(), item.defaultLanguage()).title();
    }
}
