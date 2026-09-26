package io.mateu.ecdemo1.mapping.proposals;

import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import io.mateu.ecdemo1.mapping.config.MappingProperties;
import io.mateu.ecdemo1.mapping.outbox.Outbox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Tells whoever reviews mappings that the agent has left proposals for them. */
@Component
@RequiredArgsConstructor
public class ProposalAnnouncer {

    /** What every "proposals to review" notification is about: all of them close when none is left. */
    public static final String SUBJECT = "mapping-proposals";

    final Outbox outbox;
    final MappingProperties properties;
    final Clock clock;

    @Transactional
    public void proposalsReady(int count) {
        var now = clock.instant();
        outbox.appendNotification(new NotificationRequested(UUID.randomUUID().toString(),
                NotificationType.PROPOSAL_READY, null, SUBJECT,
                "%d mapping proposal(s) to review".formatted(count),
                "The agent proposed %d equivalence(s). None is in force until someone approves it.".formatted(count),
                properties.consoleUrl() + "/mapping/dictionary",
                "proposal-ready:" + now.toEpochMilli(), now));
    }
}
