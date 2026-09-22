package io.mateu.ecdemo1.mapping.dictionary;

import io.mateu.ecdemo1.integration.model.mapping.CodeEntry;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.mapping.clients.IntegrationClients;
import io.mateu.ecdemo1.mapping.store.EntryStatus;
import io.mateu.ecdemo1.mapping.store.MappingEntry;
import io.mateu.ecdemo1.mapping.store.MappingEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * What is left to map for a hotel, and what the PMS offers to map it to — the material of a
 * proposal, for a person or for the agent.
 */
@Service
@RequiredArgsConstructor
public class Pending {

    final IntegrationClients clients;
    final Dictionary dictionary;
    final MappingEntryRepository entries;

    /** @param proposed whether a proposal for it already waits for a decision */
    public record PendingCode(CodeType type, String code, String description, boolean proposed) {
    }

    /**
     * The CRS codes this hotel can emit that have no approved equivalent. This is the full catalog
     * contrast of F010 — noisy by nature: it includes codes no reservation of this hotel uses. The
     * causes are the short, actionable list.
     */
    public List<PendingCode> pendingCodes(String hotelCode) {
        var proposed = entries.findByStatusOrderByCreatedAtDesc(EntryStatus.PROPOSED);
        return clients.crsCatalog().stream()
                .filter(e -> e.type() != CodeType.HOTEL || e.code().equals(hotelCode))
                .filter(e -> e.hotelCode() == null || e.hotelCode().equals(hotelCode))
                .filter(e -> dictionary.resolve(hotelCode, e.type(), e.code()).isEmpty())
                .map(e -> new PendingCode(e.type(), e.code(), e.description(),
                        proposed.stream().anyMatch(p -> matches(p, e, hotelCode))))
                .toList();
    }

    /**
     * The PMS's codes for this hotel. Until the hotel itself is mapped only the PMS's hotels can be
     * listed — its property codes hang from the PMS's id for it.
     */
    public List<CodeEntry> pmsCatalog(String hotelCode) {
        var pmsHotel = dictionary.resolve(hotelCode, CodeType.HOTEL, hotelCode);
        return clients.pmsCatalog(pmsHotel.map(t -> t.targetCode()).orElse(null));
    }

    static boolean matches(MappingEntry p, CodeEntry e, String hotelCode) {
        return p.type == e.type() && p.sourceCode.equals(e.code())
                && (p.hotelCode == null || p.hotelCode.equals(hotelCode));
    }
}
