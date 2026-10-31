package io.mateu.ecdemo1.contenthub.application.out;

import java.util.List;

public interface Mailer {

    /** Sends one message to several recipients (Bcc); throws when the relay does not take it. */
    void send(List<String> recipients, String subject, String html);
}
