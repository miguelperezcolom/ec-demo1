package io.mateu.ecdemo1.mapping.proposals;

import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import io.mateu.ecdemo1.mapping.config.MappingProperties;
import io.mateu.ecdemo1.mapping.outbox.Outbox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
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
        proposalsReady(count, null);
    }

    /**
     * @param hotelCode the hotel every proposal is for, or null when they span several (or the chain):
     *                  the notice then links to the Dictionary already filtered by that hotel's integration
     */
    @Transactional
    public void proposalsReady(int count, String hotelCode) {
        proposalsReady(count, hotelCode, List.of());
    }

    /**
     * @param leftOut the pending codes the agent left without a proposal, each with why when it said
     *                so: the notice names them, because the integration keeps waiting for a mapping
     *                until each has one, and "43 proposals" alone reads as if that were everything
     */
    @Transactional
    public void proposalsReady(int count, String hotelCode, List<String> leftOut) {
        var now = clock.instant();
        var link = properties.consoleUrl() + "/mapping/dictionary"
                + (hotelCode == null || hotelCode.isBlank() ? "" : "?integration=" + hotelCode);
        outbox.appendNotification(new NotificationRequested(UUID.randomUUID().toString(),
                NotificationType.PROPOSAL_READY, hotelCode, SUBJECT,
                title(count, leftOut), body(count, leftOut), link,
                "proposal-ready:" + now.toEpochMilli(), now));
    }

    public static String title(int count, List<String> leftOut) {
        return "%d mapping proposal(s) to review".formatted(count)
                + (leftOut.isEmpty() ? "" : ", %d code(s) left without one".formatted(leftOut.size()));
    }

    public static String body(int count, List<String> leftOut) {
        var body = "The agent proposed %d equivalence(s). None is in force until someone approves it.".formatted(count);
        return leftOut.isEmpty() ? body
                : body + " Still without a proposal, so the integration keeps waiting for a mapping: "
                + String.join("; ", leftOut) + ".";
    }
}
