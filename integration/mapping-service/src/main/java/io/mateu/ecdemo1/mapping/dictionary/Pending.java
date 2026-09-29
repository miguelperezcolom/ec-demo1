package io.mateu.ecdemo1.mapping.dictionary;

import io.mateu.ecdemo1.integration.model.integration.IntegrationStatus;
import io.mateu.ecdemo1.integration.model.integration.IntegrationView;
import io.mateu.ecdemo1.integration.model.mapping.CodeEntry;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.mapping.clients.IntegrationClients;
import io.mateu.ecdemo1.mapping.store.EntryStatus;
import io.mateu.ecdemo1.mapping.store.MappingEntry;
import io.mateu.ecdemo1.mapping.store.MappingEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
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
        var catalog = clients.crsCatalog();
        // A hotel with codes of its own of a type (MRU01's rate plans, boards…) emits those, never the
        // chain's of that type: the chain's are not its to map.
        var own = catalog.stream().filter(e -> hotelCode.equals(e.hotelCode())).map(CodeEntry::type)
                .collect(java.util.stream.Collectors.toSet());
        return catalog.stream()
                .filter(e -> e.type() != CodeType.HOTEL || e.code().equals(hotelCode))
                .filter(e -> e.hotelCode() == null ? !own.contains(e.type()) : e.hotelCode().equals(hotelCode))
                .filter(e -> dictionary.resolve(hotelCode, e.type(), e.code()).isEmpty())
                .map(e -> new PendingCode(e.type(), e.code(), e.description(),
                        proposed.stream().anyMatch(p -> matches(p, e, hotelCode))))
                .toList();
    }

    /**
     * The pending codes of this hotel no proposal covers yet — what an agent that has just proposed
     * still left out. The integration stays waiting for a mapping until each has one.
     */
    public List<PendingCode> withoutProposal(String hotelCode) {
        return pendingCodes(hotelCode).stream().filter(p -> !p.proposed()).toList();
    }

    /**
     * The hotels of every integration still in service with a CRS code no equivalence and no proposal
     * covers — what "ask the agent" with nothing chosen asks about. A hotel whose codes cannot be read
     * now is left out, not failed: the others are still worth asking for.
     */
    public List<String> hotelsWithPending() {
        return clients.integrations().stream()
                .filter(i -> i.status() != IntegrationStatus.DECOMMISSIONED && i.crsHotelCode() != null)
                .map(i -> i.crsHotelCode())
                .distinct()
                .filter(hotel -> {
                    try {
                        return !withoutProposal(hotel).isEmpty();
                    } catch (RuntimeException e) {
                        return false;
                    }
                })
                .toList();
    }

    /**
     * The PMS's codes for this hotel. Until the hotel itself is mapped only the PMS's hotels can be
     * listed — its property codes hang from the PMS's id for it.
     */
    public List<CodeEntry> pmsCatalog(String hotelCode) {
        // The integration names its Opera property; the hotel's equivalence, for a hotel without one.
        var pmsHotel = integratedProperty(hotelCode)
                .or(() -> dictionary.resolve(hotelCode, CodeType.HOTEL, hotelCode).map(t -> t.targetCode()));
        return clients.pmsCatalog(pmsHotel.orElse(null));
    }

    /** One code the PMS offers, and — for a chain-level entry — the properties that have it. */
    public record PmsCode(String code, String description, List<String> properties) {
    }

    /**
     * What an equivalence of this type can translate to: for a hotel, its property's codes of the
     * type; for the chain, those of every integrated property — a chain-level equivalence is valid in
     * all of them — each with the properties that have it, when not all of them do. HOTEL and
     * PARTNER_TYPE are the PMS's own, the same whatever the property.
     */
    public List<PmsCode> pmsCodes(String hotelCode, CodeType type) {
        if (type == null) {
            return List.of();
        }
        if (hotelCode != null && !hotelCode.isBlank()) {
            return pmsCatalog(hotelCode).stream().filter(c -> c.type() == type)
                    .map(c -> new PmsCode(c.code(), c.description(), List.of())).distinct().toList();
        }
        if (type == CodeType.HOTEL || type == CodeType.PARTNER_TYPE) {
            return clients.pmsCatalog(null).stream().filter(c -> c.type() == type)
                    .map(c -> new PmsCode(c.code(), c.description(), List.of())).toList();
        }
        var properties = clients.integrations().stream()
                .filter(i -> i.status() != IntegrationStatus.DECOMMISSIONED && i.pmsHotelCode() != null)
                .map(IntegrationView::pmsHotelCode).distinct().toList();
        var byCode = new LinkedHashMap<String, PmsCode>();
        for (var property : properties) {
            clients.pmsCatalog(property).stream().filter(c -> c.type() == type).forEach(c -> byCode.merge(c.code(),
                    new PmsCode(c.code(), c.description(), List.of(property)),
                    (a, b) -> new PmsCode(a.code(), a.description(),
                            java.util.stream.Stream.concat(a.properties().stream(), b.properties().stream()).distinct().toList())));
        }
        return byCode.values().stream()
                .map(c -> c.properties().size() == properties.size() ? new PmsCode(c.code(), c.description(), List.of()) : c)
                .toList();
    }

    java.util.Optional<String> integratedProperty(String hotelCode) {
        try {
            return clients.integration(hotelCode).map(i -> i.pmsHotelCode());
        } catch (RuntimeException e) {
            return java.util.Optional.empty();
        }
    }

    static boolean matches(MappingEntry p, CodeEntry e, String hotelCode) {
        return p.getType() == e.type() && p.getSourceCode().equals(e.code())
                && (p.getHotelCode() == null || p.getHotelCode().equals(hotelCode));
    }
}
