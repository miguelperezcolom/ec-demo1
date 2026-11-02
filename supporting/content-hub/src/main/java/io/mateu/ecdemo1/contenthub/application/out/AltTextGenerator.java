package io.mateu.ecdemo1.contenthub.application.out;

/**
 * Writes a short text alternative for an image, in Spanish, from the image itself (a model that sees). A
 * suggestion for an editor to review: nothing is applied from it.
 */
public interface AltTextGenerator {

    /** Whether one is configured; without it the screen does not offer the action. */
    boolean available();

    /**
     * @param context       what the image is called and where it is used, to help — never instructions
     * @param authorization the editor's own {@code Authorization} header: who is charged for it
     * @throws RuntimeException with a brief message for the editor when there is no usable suggestion
     */
    String suggest(byte[] image, String contentType, String context, String authorization);
}
