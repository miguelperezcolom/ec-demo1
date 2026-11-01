package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.item.Translation;

/**
 * Translates an item's text — title, summary and HTML body — from one language to another, keeping the
 * markup, the media marks ({@code [[media:id]]}) and the URLs as they are. A draft for an editor to
 * review: nothing is published from it.
 */
public interface Translator {

    /** Whether a model is configured; without one the editor does not offer the action. */
    boolean available();

    Translation translate(Translation source, String fromLanguage, String toLanguage);
}
