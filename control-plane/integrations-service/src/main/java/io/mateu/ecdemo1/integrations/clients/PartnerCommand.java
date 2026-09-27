package io.mateu.ecdemo1.integrations.clients;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * What the integration asks of the master of partners (the ERP) without waiting for an answer, in the
 * ERP's terms: its {@code partner-commands} topic, which it consumes once per {@link #commandId()}.
 * Sent through this service's outbox. The ERP owns the contract — it does not know the integration's
 * model — so this mirrors what it reads.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PartnerCommand.ResyncPartner.class, name = "resync-partner"),
        @JsonSubTypes.Type(value = PartnerCommand.ImportPartner.class, name = "import-partner"),
})
public sealed interface PartnerCommand {

    String commandId();

    /** The partner's code: the Kafka key, so the commands about one partner stay in order. */
    String partnerCode();

    /** Announce the partner again, unchanged, so that the integration projects it to the PMS. */
    record ResyncPartner(String commandId, String partnerCode) implements PartnerCommand {
    }

    /**
     * A partner as the PMS has it: created in the ERP if it is not there — who pays being the guest
     * until the ERP says otherwise — or its name and type brought up to date, keeping what only the
     * ERP knows; and which PMS profile it is recorded, so that it is never created there again.
     *
     * @param partnerType the ERP's: TravelAgent, TourOperator, Company or OnlineAgency
     */
    record ImportPartner(String commandId, String partnerCode, String partnerType, String name,
                         String pmsProfileId, String profileType) implements PartnerCommand {
    }

    @JsonIgnore
    default String key() {
        return partnerCode();
    }
}
