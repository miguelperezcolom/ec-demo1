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
            return entry.getHotelCode();
        }
        if (parameters.get("proposal") instanceof Dictionary.Proposal proposal) {
            return proposal.hotelCode();
        }
        if (parameters.get("entryId") instanceof String id) {
            return entries.findById(id).map(MappingEntry::getHotelCode).orElse(null);
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
            return "%s %s → %s: %s%s".formatted(e.getType(), e.getSourceCode(), e.getTargetCode(), e.getStatus(),
                    e.getEntryVersion() > 0 ? " (v" + e.getEntryVersion() + ")" : "");
        }
        if (result instanceof io.mateu.ecdemo1.mapping.causes.Causes.Discarded d) {
            return discarded(d);
        }
        if (result instanceof java.util.List<?> list && !list.isEmpty()
                && list.stream().allMatch(io.mateu.ecdemo1.mapping.causes.Causes.Discarded.class::isInstance)) {
            return list.stream().map(d -> discarded((io.mateu.ecdemo1.mapping.causes.Causes.Discarded) d))
                    .collect(java.util.stream.Collectors.joining("; "));
        }
        return "Done";
    }

    static String discarded(io.mateu.ecdemo1.mapping.causes.Causes.Discarded d) {
        return d.processKey() + " discarded; " + (d.engineCancelRequested() ? "engine cancellation requested"
                : "to be cancelled by hand in Admin → Processes");
    }
}
