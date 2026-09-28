package io.mateu.ecdemo1.integration.model.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;

/**
 * What another service asks of the mapping without waiting for an answer: sent through the sender's
 * outbox on the {@code mapping-commands} topic, and taken once — the mapping deduplicates on
 * {@link #commandId()} in its inbox. Every one is also idempotent in itself: taken twice, the second
 * changes nothing.
 *
 * <p>What a sender needs to know now — what is pending, the gaps, whether a partner is a PMS profile —
 * it still asks over HTTP: those are queries, not commands.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MappingCommand.DefineEquivalence.class, name = "define-equivalence"),
        @JsonSubTypes.Type(value = MappingCommand.RequestAgentProposal.class, name = "request-agent-proposal"),
        @JsonSubTypes.Type(value = MappingCommand.ResolveCauseIfOpen.class, name = "resolve-cause-if-open"),
        @JsonSubTypes.Type(value = MappingCommand.RecordPartnerProfile.class, name = "record-partner-profile"),
})
public sealed interface MappingCommand {

    /** Unique per command; the mapping deduplicates on it. */
    String commandId();

    /** The Kafka key: the commands about one hotel, cause or partner stay in order. */
    @JsonIgnore
    String key();

    /**
     * An equivalence entered directly, approved by whoever enters it — the hotel's own (which Opera
     * property the CRS hotel is), or which Opera profile type a partner type is. Nothing new if the
     * same equivalence is already in force. {@code hotelCode} null is the chain's.
     */
    record DefineEquivalence(String commandId, CodeType codeType, String hotelCode, String sourceCode,
                             String targetCode, String by) implements MappingCommand {
        @Override
        public String key() {
            return hotelCode == null ? "chain" : hotelCode;
        }
    }

    /** Asks the mapping agent to propose the hotel's pending codes. It answers in the background. */
    record RequestAgentProposal(String commandId, String hotelCode) implements MappingCommand {
        @Override
        public String key() {
            return hotelCode;
        }
    }

    /** Resolves a cause if it is open: whatever waited only on it goes on. Nothing if it is not open. */
    record ResolveCauseIfOpen(String commandId, String causeKey, String by) implements MappingCommand {
        @Override
        public String key() {
            return causeKey;
        }
    }

    /** Which PMS profile a partner already is, as an import from the PMS found it. */
    record RecordPartnerProfile(String commandId, String partnerCode, String pmsProfileId, String profileType)
            implements MappingCommand {
        @Override
        public String key() {
            return partnerCode;
        }
    }
}
