package io.mateu.ecdemo1.contenthub.application.out;

/**
 * The links a newsletter email carries: the open pixel and each item's link, through the tracking
 * endpoint. {@link #NONE} (no tracking: the preview, a test) links the items straight to the portal.
 */
public interface NewsletterLinks {

    /** The pixel's URL, or null for none. */
    String open(String editionId, String group);

    /** The item's link: through the tracker, or {@code direct} as it is. */
    String click(String editionId, String group, String itemId, String direct);

    NewsletterLinks NONE = new NewsletterLinks() {
        @Override
        public String open(String editionId, String group) {
            return null;
        }

        @Override
        public String click(String editionId, String group, String itemId, String direct) {
            return direct;
        }
    };
}
