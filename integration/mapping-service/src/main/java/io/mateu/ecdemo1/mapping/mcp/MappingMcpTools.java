package io.mateu.ecdemo1.mapping.mcp;

import io.mateu.ecdemo1.integration.model.mapping.CodeEntry;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
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
                  indicando confianza (0..1) y el porqué de cada línea. El hotel (tipo HOTEL) no se propone: lo
                  registra su integración al verificar la conexión con Opera, y sin ella no hay catálogo.
                - Todo código pendiente necesita una propuesta: listPendingCodes los da todos de una vez (sin
                  páginas) y proposeMappings contesta cuáles siguen sin propuesta; mientras quede alguno, propónlo.
                  Si nada del PMS encaja bien, propón el más cercano con confianza baja (0.5 o menos) y di en el
                  porqué qué falta en el PMS. Solo si el PMS no tiene ningún código de ese tipo va en unmatched,
                  con el motivo — la persona que revisa lo verá en el aviso.
                - Un CHANNEL del CRS es en Opera un sourceCode (targetCode) y un marketCode (atributo marketCode).
                  El sourceCode dice por dónde llega la reserva (web del hotel, teléfono, email, walk-in, ventas
                  de grupos, central de reservas); el marketCode, el segmento que la vende (BAR, negociada, OTA,
                  grupos…). Un canal de intermediario — turoperador, OTA — cuyo sourceCode propio no existe en la
                  propiedad (ni TO, ni OTA, ni agencia) no se inventa ni se disfraza de otro: se propone el
                  sourceCode por el que de verdad llega (la central de reservas, CRSN, si la hay: la reserva entra
                  desde el CRS), el marketCode que sí nombra el segmento (NEG para un contrato negociado, OTA para
                  una agencia online), confianza baja (0.5 o menos) y en el porqué: «la propiedad no tiene un
                  sourceCode de turoperador/OTA; el segmento lo lleva el marketCode; conviene crear uno en Opera».
                  Nunca HWEB para una OTA: es la web del propio hotel.
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

    @Tool(description = "The CRS codes a hotel can emit that have no approved equivalent in the PMS yet — every "
            + "one of them in a single answer, there is no paging. proposed=true means a proposal for it already "
            + "waits for a person's decision; each one with proposed=false needs a proposal from you")
    public List<Pending.PendingCode> listPendingCodes(@ToolParam(description = "CRS hotel code, e.g. PMI01") String hotelCode) {
        return pending.pendingCodes(hotelCode);
    }

    @Tool(description = "The PMS's codes for a hotel, to choose equivalents from. Until the hotel itself is "
            + "mapped, only the PMS's hotels are listed")
    public List<CodeEntry> getPmsCatalog(@ToolParam(description = "CRS hotel code, e.g. PMI01") String hotelCode) {
        return pending.pmsCatalog(hotelCode);
    }

    /** A pending code the agent could not propose anything for, and why. */
    public record Unmatched(CodeType type, String hotelCode, String code, String reason) {
    }

    @Tool(description = "Register proposed equivalences for a person to review. hotelCode empty means a "
            + "chain-level equivalence, valid for every hotel unless one has its own. Every pending code needs a "
            + "proposal: when nothing in the PMS fits well, propose the closest with low confidence and say why "
            + "in the rationale. Only a code the PMS has no code of its type for goes in unmatched, with the "
            + "reason. Returns the proposal ids and which pending codes of the hotel are still without a proposal")
    public String proposeMappings(List<Dictionary.Proposal> proposals,
                                  @ToolParam(required = false, description = "Pending codes you cannot propose "
                                          + "anything for, each with the reason; the person reviewing is told")
                                  List<Unmatched> unmatched) {
        return propose(proposals == null ? List.of() : proposals, unmatched == null ? List.of() : unmatched);
    }

    String propose(List<Dictionary.Proposal> proposals, List<Unmatched> unmatched) {
        var ids = new ArrayList<String>();
        var errors = new ArrayList<String>();
        for (var proposal : proposals) {
            try {
                ids.add(dictionary.propose(proposal, "agent").getId());
            } catch (RuntimeException e) {
                errors.add(proposal.sourceCode() + ": " + e.getMessage());
            }
        }
        var hotels = java.util.stream.Stream.concat(proposals.stream().map(Dictionary.Proposal::hotelCode),
                        unmatched.stream().map(Unmatched::hotelCode))
                .filter(h -> h != null && !h.isBlank()).distinct().toList();
        var coverage = hotels.stream().map(h -> coverage(h, unmatched)).toList();
        var leftOut = coverage.stream().flatMap(c -> c.leftOut().stream()).toList();
        if (!ids.isEmpty() || !leftOut.isEmpty()) {
            // One hotel and nothing chain-level: the notice links to that hotel's integration.
            var hotel = hotels.size() == 1 && proposals.stream().allMatch(p -> hotels.get(0).equals(p.hotelCode()))
                    ? hotels.get(0) : null;
            announcer.proposalsReady(ids.size(), hotel, leftOut);
        }
        log.info("Agent proposed {} equivalence(s), {} refused, {} left without a proposal", ids.size(), errors.size(),
                leftOut.size());
        return "Proposed %d, ids %s%s%s".formatted(ids.size(), ids,
                errors.isEmpty() ? "" : "; refused: " + errors,
                coverage.stream().map(Coverage::toolAnswer).collect(java.util.stream.Collectors.joining()));
    }

    /**
     * What is still without a proposal in a hotel once these are in. It goes back to the agent, so a
     * first pass that skipped a code — a channel with no obvious source code in the PMS — is told so
     * and can complete it in the same conversation; and to the notice, so the person reviewing knows.
     */
    Coverage coverage(String hotelCode, List<Unmatched> unmatched) {
        try {
            // One declared without a hotel is taken as for the hotel(s) of this call.
            var declared = unmatched.stream()
                    .filter(u -> u.hotelCode() == null || u.hotelCode().isBlank() || hotelCode.equals(u.hotelCode()))
                    .toList();
            var left = pending.withoutProposal(hotelCode);
            var silent = left.stream().filter(p -> p.type() != CodeType.HOTEL)
                    .filter(p -> declared.stream().noneMatch(u -> u.type() == p.type() && p.code().equals(u.code())))
                    .toList();
            var said = left.stream().flatMap(p -> declared.stream()
                    .filter(u -> u.type() == p.type() && p.code().equals(u.code()))
                    .map(u -> p.type() + " " + p.code() + " — " + (u.reason() == null || u.reason().isBlank()
                            ? "no reason given" : u.reason().strip()))).toList();
            var leftOut = new ArrayList<>(said);
            silent.forEach(p -> leftOut.add(p.type() + " " + p.code() + " — the agent gave no proposal and no reason"));
            return new Coverage(hotelCode, silent, said, leftOut);
        } catch (RuntimeException e) {
            return new Coverage(hotelCode, List.of(), List.of(), List.of(), e.getMessage());
        }
    }

    record Coverage(String hotelCode, List<Pending.PendingCode> silent, List<String> declared, List<String> leftOut,
                    String unreadable) {
        Coverage(String hotelCode, List<Pending.PendingCode> silent, List<String> declared, List<String> leftOut) {
            this(hotelCode, silent, declared, leftOut, null);
        }

        String toolAnswer() {
            if (unreadable != null) {
                return "; could not check what is left of %s: %s".formatted(hotelCode, unreadable);
            }
            if (!silent.isEmpty()) {
                return ("; still without a proposal in %s (%d): %s. Propose each of them too, with low confidence if "
                        + "nothing fits well and why in the rationale — or, only if the PMS has no code of its type, "
                        + "put it in unmatched with the reason").formatted(hotelCode, silent.size(),
                        silent.stream().map(p -> p.type() + " " + p.code() + " «" + p.description() + "»")
                                .collect(java.util.stream.Collectors.joining(", ")));
            }
            return declared.isEmpty() ? "; every pending code of %s has a proposal".formatted(hotelCode)
                    : "; every pending code of %s has a proposal, but %d you left unmatched: %s".formatted(hotelCode,
                    declared.size(), String.join("; ", declared));
        }
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

    @Tool(description = "Withdraw an equivalence in force, with no replacement: the code has no equivalence again and "
            + "what needs it waits for a new one. ONLY when the user has explicitly asked for it; withdrawnBy is the "
            + "user's name. To correct an equivalence, propose the right one instead")
    public String withdrawMapping(String entryId, String withdrawnBy) {
        if (withdrawnBy == null || withdrawnBy.isBlank() || "agent".equalsIgnoreCase(withdrawnBy)) {
            return "Error: a withdrawal needs the name of the person withdrawing it";
        }
        try {
            var entry = dictionary.withdraw(entryId, withdrawnBy);
            return "Withdrawn %s %s → %s (%s)".formatted(entry.getType(), entry.getSourceCode(), entry.getTargetCode(), entry.scope());
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
