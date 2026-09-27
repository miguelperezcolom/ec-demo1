package io.mateu.ecdemo1.mapping.audit;

import io.mateu.ecdemo1.mapping.dictionary.Dictionary;
import io.mateu.ecdemo1.mapping.store.CauseRecordRepository;
import io.mateu.ecdemo1.mapping.store.MappingEntry;
import io.mateu.ecdemo1.mapping.store.MappingEntryRepository;
import io.mateu.ecdemo1.mapping.store.WaiterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * A mapping decision is about its hotel — none, for a chain-wide equivalence — and a cause or a
 * waiting process about the hotel it blocks.
 */
@Component
@RequiredArgsConstructor
public class MappingAuditSubjects implements AuditSubjects {

    final MappingEntryRepository entries;
    final CauseRecordRepository causes;
    final WaiterRepository waiters;

    @Override
    public String service() {
        return "mapping";
    }

    @Override
    public String hotel(Map<String, Object> parameters, Object result) {
        if (result instanceof MappingEntry entry) {
            return entry.hotelCode;
        }
        if (parameters.get("proposal") instanceof Dictionary.Proposal proposal) {
            return proposal.hotelCode();
        }
        if (parameters.get("entryId") instanceof String id) {
            return entries.findById(id).map(e -> e.hotelCode).orElse(null);
        }
        if (parameters.get("causeKey") instanceof String key) {
            return causes.findById(key).map(c -> c.hotelCode).orElse(null);
        }
        if (parameters.get("processKey") instanceof String key) {
            return waiters.findById(key).map(w -> w.getHotelCode()).orElse(null);
        }
        return null;
    }

    @Override
    public String response(Object result) {
        if (result instanceof MappingEntry e) {
            return "%s %s → %s: %s%s".formatted(e.type, e.sourceCode, e.targetCode, e.status,
                    e.entryVersion > 0 ? " (v" + e.entryVersion + ")" : "");
        }
        return "Done";
    }
}
