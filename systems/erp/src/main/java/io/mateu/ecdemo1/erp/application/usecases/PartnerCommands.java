package io.mateu.ecdemo1.erp.application.usecases;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.mateu.ecdemo1.erp.application.out.Inbox;
import io.mateu.ecdemo1.erp.domain.partner.PartnerType;
import io.mateu.ecdemo1.erp.domain.partner.PmsProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What other services ask of the master of partners without waiting for an answer — its
 * {@code partner-commands} topic, the integration's way to it: announce a partner again, bring one in
 * as the PMS has it, record which PMS profile it is. Each is taken once: its id goes into the inbox in
 * the same transaction as what it does, and a repetition does nothing.
 *
 * <p>This is the ERP's contract, in its own terms: whoever sends these writes them as they are here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartnerCommands {

    static final String CONSUMER = "partner-commands";

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Resync.class, name = "resync-partner"),
            @JsonSubTypes.Type(value = Import.class, name = "import-partner"),
            @JsonSubTypes.Type(value = RecordPmsProfile.class, name = "record-pms-profile"),
    })
    public sealed interface Command {
        String commandId();

        String partnerCode();
    }

    /** Announce the partner again, unchanged, so the integration projects it once more. */
    public record Resync(String commandId, String partnerCode) implements Command {
    }

    /**
     * A partner as the PMS has it: created if the master does not have it — the guest paying until
     * someone here says otherwise — or its name and type brought up to date, keeping the rest; and the
     * PMS profile it is recorded.
     */
    public record Import(String commandId, String partnerCode, PartnerType partnerType, String name,
                         String pmsProfileId, String profileType) implements Command {
    }

    /** Which profile the partner is in the PMS — the one the integration created, or found there. */
    public record RecordPmsProfile(String commandId, String partnerCode, String profileId, String profileType)
            implements Command {
    }

    final Inbox inbox;
    final PartnerService partners;

    /**
     * @return false for a command already taken
     * @throws IllegalArgumentException for a command that cannot be carried out — no id, a partner
     *                                  the master does not have, details it would not take
     */
    @Transactional
    public boolean handle(Command command) {
        if (command.commandId() == null || command.commandId().isBlank()) {
            throw new IllegalArgumentException("A command needs its id: " + command);
        }
        if (!inbox.firstTime(CONSUMER, command.commandId())) {
            log.debug("Already taken: {}", command);
            return false;
        }
        switch (command) {
            case Resync c -> partners.resync(c.partnerCode());
            case Import c -> partners.importFromPms(c.partnerCode(), c.partnerType(), c.name(),
                    new PmsProfile(c.pmsProfileId(), c.profileType()));
            case RecordPmsProfile c -> partners.recordPmsProfile(c.partnerCode(), new PmsProfile(c.profileId(), c.profileType()));
        }
        log.info("Taken: {}", command);
        return true;
    }
}
