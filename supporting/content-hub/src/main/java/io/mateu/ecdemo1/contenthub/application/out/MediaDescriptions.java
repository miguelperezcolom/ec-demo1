package io.mateu.ecdemo1.contenthub.application.out;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * The text alternative of each image of the library — what a screen reader says, and what shows if the
 * image does not load. Apart from the medium itself, which is never modified.
 */
public interface MediaDescriptions {

    Optional<String> altText(String mediaId);

    Map<String, String> all();

    void describe(String mediaId, String altText, String by, Instant at);

    void forget(String mediaId);
}
