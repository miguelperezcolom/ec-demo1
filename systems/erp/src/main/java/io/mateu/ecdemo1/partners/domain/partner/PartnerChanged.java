package io.mateu.ecdemo1.partners.domain.partner;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.mateu.workflow.ddd.DomainEvent;

import java.time.Instant;

/**
 * A partner was created or changed, or someone asked for it to be synchronised again. Thin: whoever
 * needs the partner reads it. Keyed by the partner code.
 */
@JsonTypeName("partner-changed")
public record PartnerChanged(String eventId, String partnerCode, long version, Instant occurredAt)
        implements DomainEvent {

    @Override
    public String partitionKey() {
        return partnerCode;
    }
}
