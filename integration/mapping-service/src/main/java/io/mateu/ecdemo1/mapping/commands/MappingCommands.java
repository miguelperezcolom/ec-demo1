package io.mateu.ecdemo1.mapping.commands;

import io.mateu.ecdemo1.integration.model.command.MappingCommand;
import io.mateu.ecdemo1.messaging.Inbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What other services ask of the mapping on the {@code mapping-commands} topic — today the
 * integrations service: an onboarding's equivalences, the agent's proposal, the resolution of a
 * cause, a partner's PMS profile. Each is taken once: its id goes into the inbox in the same
 * transaction as what it does, and a repetition does nothing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MappingCommands {

    static final String CONSUMER = "mapping-commands";

    /** What the commands do, apart from how they arrive. */
    public interface Effects {
        void defineUnlessInForce(MappingCommand.DefineEquivalence command);

        /** Asks the agent — once the command is taken for good: after the transaction commits. */
        void requestAgentProposal(String hotelCode);

        void resolveCauseIfOpen(String causeKey, String by);

        void recordPartnerProfile(String partnerCode, String pmsProfileId, String profileType);
    }

    final Inbox inbox;
    final Effects effects;

    /** @return false for a command already taken */
    @Transactional
    public boolean handle(MappingCommand command) {
        if (command.commandId() == null || command.commandId().isBlank()) {
            throw new IllegalArgumentException("A command needs its id: " + command);
        }
        if (!inbox.firstTime(CONSUMER, command.commandId())) {
            log.debug("Already taken: {}", command);
            return false;
        }
        switch (command) {
            case MappingCommand.DefineEquivalence c -> effects.defineUnlessInForce(c);
            case MappingCommand.RequestAgentProposal c -> effects.requestAgentProposal(c.hotelCode());
            case MappingCommand.ResolveCauseIfOpen c -> effects.resolveCauseIfOpen(c.causeKey(), c.by());
            case MappingCommand.RecordPartnerProfile c -> effects.recordPartnerProfile(c.partnerCode(), c.pmsProfileId(),
                    c.profileType());
        }
        log.info("Taken: {}", command);
        return true;
    }
}
