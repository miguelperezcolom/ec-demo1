package io.mateu.ecdemo1.communication.routing;

import io.mateu.ecdemo1.communication.send.GoogleChat;
import io.mateu.ecdemo1.communication.store.RecipientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The recipients table read as a {@link Plan}. The table is the only rule: nothing in the
 * configuration says who sees to what any more.
 */
@Service
@RequiredArgsConstructor
public class Routing {

    final RecipientRepository recipients;
    final GoogleChat chat;

    public Plan plan(String type, String hotel) {
        return Plan.of(recipients.findAll(), type, hotel, chat.webhooks().size());
    }
}
