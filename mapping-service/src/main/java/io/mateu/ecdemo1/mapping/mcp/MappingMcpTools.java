package io.mateu.ecdemo1.mapping.mcp;

import io.mateu.ecdemo1.integration.model.mapping.CodeEntry;
import io.mateu.ecdemo1.mapping.dictionary.Dictionary;
import io.mateu.ecdemo1.mapping.dictionary.Pending;
import io.mateu.ecdemo1.mapping.rest.MappingController;
import io.mateu.ecdemo1.mapping.store.EntryStatus;
import io.mateu.ecdemo1.mapping.store.MappingEntryRepository;
import io.mateu.ecdemo1.mapping.proposals.ProposalAnnouncer;
import io.mateu.workflow.mcp.McpSystemContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The mapping as tools for an agent: read what is pending and what the PMS offers, and propose.
 * Proposing is the agent's to do; approving is a person's, and the tools say so.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MappingMcpTools implements McpSystemContext {

    final MappingController api;
    final Dictionary dictionary;
    final Pending pending;
    final MappingEntryRepository entries;
    final ProposalAnnouncer announcer;

    @Override
    public String getSystemContext() {
        return """
                Mapeado de códigos de la integración CRS → PMS (Opera):
                - Cada código del CRS (hotel, tipo de habitación, tarifa, régimen, canal, forma de pago, motivo
                  de cancelación, tipo de interlocutor) necesita una equivalencia aprobada en el PMS antes de
                  que una reserva que lo use pueda proyectarse. Sin ella, la reserva espera: es una causa.
                - Para proponer el mapeado pendiente de un hotel: listPendingCodes(hotel) da los códigos del CRS
                  sin equivalencia; getPmsCatalog(hotel) da los códigos del PMS entre los que elegir. Empareja
                  por significado, no por parecido de nombre, y registra la propuesta con proposeMappings,
                  indicando confianza (0..1) y el porqué de cada línea. El hotel (tipo HOTEL) se mapea primero:
                  sin él no hay catálogo de la propiedad.
                - Un CHANNEL del CRS es en Opera un sourceCode (targetCode) y un marketCode (atributo marketCode).
                - Un PARTNER_TYPE es el tipo de perfil de Opera: TRAVEL_AGENT, COMPANY o SOURCE.
                - Nada de lo que propongas entra en vigor sin que una persona lo apruebe. No apruebes tú: si el
                  usuario te pide aprobar, hazlo solo con approveMapping indicando su nombre y después de que
                  confirme expresamente esa línea.
                - listOpenCauses dice qué está bloqueando procesos y cuántos esperan detrás de cada causa.
                """;
    }

    @Tool(description = "Open causes blocking processes, with how many processes wait on each")
    public List<MappingController.CauseView> listOpenCauses() {
        return api.causes(true);
    }

    @Tool(description = "The CRS codes a hotel can emit that have no approved equivalent in the PMS yet")
    public List<Pending.PendingCode> listPendingCodes(@ToolParam(description = "CRS hotel code, e.g. PMI01") String hotelCode) {
        return pending.pendingCodes(hotelCode);
    }

    @Tool(description = "The PMS's codes for a hotel, to choose equivalents from. Until the hotel itself is "
            + "mapped, only the PMS's hotels are listed")
    public List<CodeEntry> getPmsCatalog(@ToolParam(description = "CRS hotel code, e.g. PMI01") String hotelCode) {
        return pending.pmsCatalog(hotelCode);
    }

    @Tool(description = "Register proposed equivalences for a person to review. hotelCode empty means a "
            + "chain-level equivalence, valid for every hotel unless one has its own. Returns the proposal ids")
    public String proposeMappings(List<Dictionary.Proposal> proposals) {
        var ids = new ArrayList<String>();
        var errors = new ArrayList<String>();
        for (var proposal : proposals) {
            try {
                ids.add(dictionary.propose(proposal, "agent").getId());
            } catch (RuntimeException e) {
                errors.add(proposal.sourceCode() + ": " + e.getMessage());
            }
        }
        if (!ids.isEmpty()) {
            announcer.proposalsReady(ids.size());
        }
        log.info("Agent proposed {} equivalence(s), {} refused", ids.size(), errors.size());
        return "Proposed %d, ids %s%s".formatted(ids.size(), ids,
                errors.isEmpty() ? "" : "; refused: " + errors);
    }

    @Tool(description = "Proposals waiting for a person's decision")
    public List<ProposalView> listProposals() {
        return entries.findByStatusOrderByCreatedAtDesc(EntryStatus.PROPOSED).stream()
                .map(e -> new ProposalView(e.getId(), e.getType().name(), e.scope(), e.getSourceCode(),
                        e.getTargetCode(), e.getAttributes(), e.getConfidence(), e.getRationale(), e.getProposedBy()))
                .toList();
    }

    public record ProposalView(String id, String type, String scope, String sourceCode, String targetCode,
                               Object attributes, Double confidence, String rationale, String proposedBy) {
    }

    @Tool(description = "Approve a proposed equivalence. ONLY when the user has explicitly confirmed this "
            + "proposal in the conversation; approvedBy is the user's name. It resumes every process waiting for it")
    public String approveMapping(String proposalId, String approvedBy) {
        if (approvedBy == null || approvedBy.isBlank() || "agent".equalsIgnoreCase(approvedBy)) {
            return "Error: an approval needs the name of the person approving it";
        }
        try {
            var entry = dictionary.approve(proposalId, approvedBy);
            return "Approved %s %s → %s (%s, v%d)".formatted(entry.getType(), entry.getSourceCode(),
                    entry.getTargetCode(), entry.scope(), entry.getEntryVersion());
        } catch (RuntimeException e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Reject a proposed equivalence")
    public String rejectMapping(String proposalId, String rejectedBy) {
        try {
            dictionary.reject(proposalId, rejectedBy);
            return "Rejected";
        } catch (RuntimeException e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Resolve a cause that is not a missing equivalence — typically a write the PMS refused, "
            + "once someone fixed what it refused. Every process waiting only on it resumes")
    public String resolveCause(String causeKey, String resolvedBy) {
        try {
            api.resolveCause(causeKey, resolvedBy);
            return "Resolved " + causeKey;
        } catch (RuntimeException e) {
            return "Error: " + e.getMessage();
        }
    }
}
