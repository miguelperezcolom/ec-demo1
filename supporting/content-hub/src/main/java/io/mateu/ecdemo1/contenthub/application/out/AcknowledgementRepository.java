package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.ack.Acknowledgement;

import java.util.Set;

public interface AcknowledgementRepository {

    /** Records it; a repetition does nothing. */
    void record(Acknowledgement acknowledgement);

    /** Who has an acknowledgement of this type for a version of the item at or above {@code minVersion}. */
    Set<String> users(String itemId, Acknowledgement.Type type, int minVersion);

    boolean has(String userId, String itemId, Acknowledgement.Type type, int minVersion);
}
