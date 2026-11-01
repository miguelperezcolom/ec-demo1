package io.mateu.ecdemo1.contenthub.application.out;

import java.util.List;

public interface Mailer {

    /** Sends one message to several recipients (Bcc); throws when the relay does not take it. */
    void send(List<String> recipients, String subject, String html);

    /**
     * Whether every message goes to one demo mailbox instead of its recipients. The newsletter then sends
     * an edition as one digest — a section per group — rather than one message per group to the same
     * inbox.
     */
    default boolean redirected() {
        return false;
    }
}
