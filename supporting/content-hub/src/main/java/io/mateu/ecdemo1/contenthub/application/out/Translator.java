package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.item.Translation;

/**
 * Translates an item's text — title, summary and HTML body — from one language to another, keeping the
 * markup, the media marks ({@code [[media:id]]}) and the URLs as they are. A draft for an editor to
 * review: nothing is published from it.
 */
public interface Translator {

    /** Whether a translator is configured; without one the editor does not offer the action. */
    boolean available();

    /**
     * @param authorization the editor's own {@code Authorization} header: it is who is charged for it
     * @throws RuntimeException with a brief message for the editor when there is no usable translation
     */
    Translation translate(Translation source, String fromLanguage, String toLanguage, String authorization);
}
