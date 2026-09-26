package io.mateu.ecdemo1.mapping.ui.pages;

import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.core.infra.declarative.orchestrators.crud.CrudActionResult;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.mapping.dictionary.Dictionary;
import io.mateu.ecdemo1.mapping.dictionary.Pending;
import io.mateu.ecdemo1.mapping.proposals.AgentProposals;
import io.mateu.ecdemo1.mapping.store.EntryStatus;
import io.mateu.ecdemo1.mapping.store.MappingEntry;
import io.mateu.ecdemo1.mapping.store.MappingEntryRepository;
import io.mateu.ecdemo1.mapping.ui.Paging;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.ListToolbarButton;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.ButtonStyle;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * The dictionary, every version of every equivalence — and, once an integration is chosen in the
 * filters, what is still unmapped for its hotel. What waits for someone comes first: the codes
 * nobody has mapped, then the proposals. An entry is never edited in place — saving one approves a
 * new version of it; an unmapped code is mapped by editing its row and saving it.
 *
 * <p>The agent is asked from here too, and approving, rejecting and withdrawing work on the selected
 * rows as well as on one entry's view.
 */
@Service
@RequiredArgsConstructor
@Scope("prototype")
@Slf4j
@Title("Dictionary")
public class DictionaryCrud extends Crud<EntryViewModel, EntryViewModel, EntryViewModel, DictionaryFilters, EntryRow, String> {

    /** A row that is not an entry: a CRS code of a hotel with no equivalent. Its id carries all of it. */
    static final String UNMAPPED = "unmapped-";

    final EntryViewModel viewModel;
    final MappingEntryRepository entries;
    final Pending pending;
    final Dictionary dictionary;
    final AgentProposals agent;

    @Override
    public ListingData<EntryRow> search(SearchRequest request, HttpRequest httpRequest) {
        var filters = filters(request);
        var hotel = filters == null || blank(filters.integration) ? null : filters.integration;
        var text = request.searchText() == null ? "" : request.searchText().toLowerCase();
        var rows = Stream.concat(unmapped(hotel), entries.findAll().stream()
                        .filter(e -> hotel == null || e.hotelCode == null || e.hotelCode.equals(hotel))
                        .sorted(Comparator.comparing((MappingEntry e) -> order(e.status))
                                .thenComparing(e -> e.type).thenComparing(e -> e.sourceCode))
                        .map(DictionaryCrud::row))
                .filter(r -> filters == null || filters.type == null || filters.type.name().equals(r.type()))
                .filter(r -> filters == null || filters.status == null || filters.status.isEmpty()
                        || filters.status.stream().anyMatch(s -> label(s).equals(r.status().message())))
                .filter(r -> (r.type() + " " + r.crsCode() + " " + nonNull(r.pmsCode()) + " " + r.scope()).toLowerCase().contains(text))
                .toList();
        return Paging.page(rows, request);
    }

    /**
     * The hotel's CRS codes with no approved equivalent and no proposal waiting — a proposal is
     * already a row of its own. The full catalog contrast, noisy by nature; the causes are the short list.
     */
    Stream<EntryRow> unmapped(String hotel) {
        if (hotel == null) {
            return Stream.empty();
        }
        try {
            return pending.pendingCodes(hotel).stream()
                    .filter(p -> !p.proposed())
                    .map(p -> new EntryRow(unmappedId(hotel, p.type(), p.code()), p.type().name(), hotel, p.code(),
                            "", null, new Status(StatusType.DANGER, label(DictionaryFilters.State.UNMAPPED)), "", ""));
        } catch (RuntimeException e) {
            log.warn("What is unmapped for {} could not be read: {}", hotel, e.getMessage());
            return Stream.empty();
        }
    }

    static int order(EntryStatus status) {
        return status == EntryStatus.PROPOSED ? 0 : status == EntryStatus.APPROVED ? 1 : 2;
    }

    static EntryRow row(MappingEntry e) {
        return new EntryRow(e.id, e.type.name(), e.scope(), e.sourceCode, e.targetCode, e.entryVersion, status(e.status),
                e.proposedBy, e.decidedBy);
    }

    static Status status(EntryStatus status) {
        return switch (status) {
            case PROPOSED -> new Status(StatusType.WARNING, "Proposed");
            case APPROVED -> new Status(StatusType.SUCCESS, "Approved");
            case REJECTED -> new Status(StatusType.DANGER, "Rejected");
            case SUPERSEDED -> new Status(StatusType.NONE, "Superseded");
            case WITHDRAWN -> new Status(StatusType.NONE, "Withdrawn");
        };
    }

    static String label(DictionaryFilters.State state) {
        return state == DictionaryFilters.State.UNMAPPED ? "Unmapped" : status(EntryStatus.valueOf(state.name())).message();
    }

    static String unmappedId(String hotel, CodeType type, String code) {
        return UNMAPPED + Base64.getUrlEncoder().withoutPadding()
                .encodeToString((hotel + "|" + type.name() + "|" + code).getBytes(StandardCharsets.UTF_8));
    }

    /** Asks the agent for a proposal for the integration chosen in the filters, or for the hotels of the selected rows. */
    @ListToolbarButton(rowsSelectedRequired = false)
    @Toolbar(buttonStyle = ButtonStyle.primary, order = 0)
    public Object askTheAgent(List<EntryRow> selection, HttpRequest httpRequest) {
        var chosen = httpRequest.runActionRq().componentState().get("integration");
        var hotels = chosen != null && !blank(chosen.toString()) ? List.of(chosen.toString())
                : (selection == null ? List.<EntryRow>of() : selection).stream()
                        .map(EntryRow::scope).filter(s -> s != null && !"chain".equals(s)).distinct().toList();
        if (hotels.isEmpty()) {
            return refreshed(httpRequest, "Choose an integration in the filters, or select rows of its hotel, to ask the agent");
        }
        if (hotels.size() > 1) {
            hotels.forEach(agent::requestProposalInBackground);
            return refreshed(httpRequest, "The agent is proposing the mapping of " + String.join(", ", hotels)
                    + "; its proposals will appear here, waiting for review");
        }
        var answer = agent.requestProposal(hotels.getFirst(), httpRequest.getHeaderValue("Authorization"));
        return refreshed(httpRequest, "The agent answered; its proposals are here, waiting for review. "
                + (answer == null ? "" : answer.lines().findFirst().orElse("")));
    }

    @ListToolbarButton
    @Toolbar(order = 1)
    public Object approve(List<EntryRow> selection, HttpRequest httpRequest) {
        return onSelected(selection, httpRequest, dictionary::approve, "approved");
    }

    @ListToolbarButton(confirmationRequired = true)
    @Toolbar(order = 2)
    @Action(confirmationTitle = "Reject the selected proposals?", confirmationMessage = "They stay in the history as rejected.")
    public Object reject(List<EntryRow> selection, HttpRequest httpRequest) {
        return onSelected(selection, httpRequest, dictionary::reject, "rejected");
    }

    @ListToolbarButton(confirmationRequired = true)
    @Toolbar(order = 3)
    @Action(confirmationTitle = "Withdraw the selected equivalences?",
            confirmationMessage = "They stop translating their codes, and nothing takes their place: what needs those codes from now on waits until someone maps them again. What was already written in Opera is not changed.")
    public Object withdraw(List<EntryRow> selection, HttpRequest httpRequest) {
        return onSelected(selection, httpRequest, dictionary::withdraw, "withdrawn");
    }

    /** One decision per selected entry; one that cannot take it is reported and does not stop the rest. */
    Object onSelected(List<EntryRow> selection, HttpRequest httpRequest,
                      BiFunction<String, String, MappingEntry> decision, String done) {
        var user = EntryViewModel.user(httpRequest);
        var count = 0;
        var refused = new ArrayList<String>();
        for (var row : selection == null ? List.<EntryRow>of() : selection) {
            if (row.id() == null || row.id().startsWith(UNMAPPED)) {
                refused.add(row.type() + " " + row.crsCode() + ": unmapped");
                continue;
            }
            try {
                decision.apply(row.id(), user);
                count++;
            } catch (RuntimeException e) {
                refused.add(row.type() + " " + row.crsCode() + ": " + e.getMessage());
            }
        }
        var text = count + " " + done + (refused.isEmpty() ? "" : "; not " + done + ": " + String.join("; ", refused));
        return refreshed(httpRequest, text);
    }

    /** The message, and the listing searched again so the rows show what was just decided. */
    static CrudActionResult refreshed(HttpRequest httpRequest, String message) {
        var initiator = httpRequest.runActionRq().initiatorComponentId();
        return CrudActionResult.of("refresh")
                .withRoute("/list")
                .withActionToRun("search")
                .withTargetComponentId("ux_" + initiator.substring(0, initiator.length() - "_app".length()) + "_cs_list")
                .withMessage(new Message(message));
    }

    @Override
    public EntryViewModel view(String id, HttpRequest httpRequest) {
        return load(id);
    }

    @Override
    public EntryViewModel edit(String id, HttpRequest httpRequest) {
        return load(id);
    }

    private EntryViewModel load(String id) {
        if (id.startsWith(UNMAPPED)) {
            var parts = new String(Base64.getUrlDecoder().decode(id.substring(UNMAPPED.length())), StandardCharsets.UTF_8)
                    .split("\\|", 3);
            return viewModel.loadUnmapped(parts[0], CodeType.valueOf(parts[1]), parts[2]);
        }
        return viewModel.load(entries.findById(id).orElseThrow(() -> new NoSuchElementException("No mapping entry " + id)));
    }

    @Override
    public EntryViewModel creationForm(HttpRequest httpRequest) {
        return viewModel;
    }

    @Override
    public String save(HttpRequest httpRequest) {
        return httpRequest.getComponentState(EntryViewModel.class).save(httpRequest);
    }

    @Override
    public String create(HttpRequest httpRequest) {
        return httpRequest.getComponentState(EntryViewModel.class).create(httpRequest);
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        throw new IllegalStateException("The dictionary keeps its history: reject a proposal, or withdraw an equivalence");
    }

    @Override
    public String getIdFieldForRow() {
        return "id";
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    static String nonNull(String s) {
        return s == null ? "" : s;
    }
}
