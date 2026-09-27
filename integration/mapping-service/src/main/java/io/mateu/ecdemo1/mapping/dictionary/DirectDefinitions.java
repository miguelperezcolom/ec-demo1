package io.mateu.ecdemo1.mapping.dictionary;

import io.mateu.ecdemo1.mapping.store.EntryStatus;
import io.mateu.ecdemo1.mapping.store.MappingEntry;
import io.mateu.ecdemo1.mapping.store.MappingEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Objects;

/**
 * An equivalence entered directly — by the integration on its own (the hotel's, the partner types), or
 * through the API — unless the same one is already in force. What the integration enters can come
 * more than once, and the second time changes nothing.
 */
@Service
@RequiredArgsConstructor
public class DirectDefinitions {

    final Dictionary dictionary;
    final MappingEntryRepository entries;

    @Transactional
    public MappingEntry defineUnlessInForce(Dictionary.Proposal proposal, String by) {
        var current = dictionary.resolve(proposal.hotelCode(), proposal.type(), proposal.sourceCode());
        if (current.isPresent() && current.get().targetCode().equals(proposal.targetCode())) {
            // In force already — the property's own, or the chain's it inherits: that is the answer, and
            // nothing new is entered. Looking only at the property's made an inherited one a 404.
            return entries.findAllByOrderByTypeAscSourceCodeAscEntryVersionDesc().stream()
                    .filter(e -> e.type == proposal.type() && e.sourceCode.equals(proposal.sourceCode())
                            && (Objects.equals(e.hotelCode, proposal.hotelCode()) || e.hotelCode == null)
                            && e.status == EntryStatus.APPROVED)
                    .sorted(Comparator.comparing(e -> e.hotelCode == null ? 1 : 0))
                    .findFirst().orElseThrow();
        }
        return dictionary.define(proposal, by);
    }
}
