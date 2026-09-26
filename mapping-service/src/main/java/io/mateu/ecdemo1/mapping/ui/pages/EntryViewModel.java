package io.mateu.ecdemo1.mapping.ui.pages;

import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.mapping.dictionary.Dictionary;
import io.mateu.ecdemo1.mapping.dictionary.Pending;
import io.mateu.ecdemo1.mapping.store.EntryStatus;
import io.mateu.ecdemo1.mapping.store.MappingEntry;
import io.mateu.ecdemo1.mapping.store.MappingEntryRepository;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.MasterDetail;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.State;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import io.mateu.uidl.interfaces.VisibilitySupplier;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * An equivalence. Created here it is approved at once, by the person creating it; a proposal — the
 * agent's, typically — is approved or rejected with the toolbar actions. Leaving the hotel empty
 * makes it a chain-level equivalence, valid for every hotel that has no exception of its own.
 *
 * <p>It is also how an unmapped code is mapped: its row opens this with the CRS code filled in, and
 * saving it with a PMS code defines the equivalence. While a code waits for a decision, what Opera
 * offers for its type is shown beside it.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class EntryViewModel implements Identifiable, VisibilitySupplier {

    @ReadOnly
    @HiddenInCreate
    Status status = new Status(StatusType.NONE, "New");

    @Section("Equivalence")
    @NotNull
    CodeType type;
    /** Empty: chain-level. */
    String hotelCode;
    @NotEmpty
    String crsCode;
    @NotEmpty
    String pmsCode;
    @MasterDetail(minHeightWhenDetailVisible = "14rem;")
    @Colspan(2)
    List<AttributeRow> attributes;

    @Section("Decision")
    @ReadOnly
    @HiddenInCreate
    String id;
    @ReadOnly
    @HiddenInCreate
    Integer version;
    @ReadOnly
    @HiddenInCreate
    String proposedBy;
    @ReadOnly
    @HiddenInCreate
    Double confidence;
    @ReadOnly
    @HiddenInCreate
    @Stereotype(FieldStereotype.textarea)
    @Colspan(2)
    String rationale;
    @ReadOnly
    @HiddenInCreate
    String decided;

    /** What Opera offers for this type at this hotel — for a code that waits for a decision. */
    @Section("What Opera offers")
    @ReadOnly
    @HiddenInCreate
    @Stereotype(FieldStereotype.grid)
    @Colspan(2)
    List<PmsCodeRow> pmsCodes = List.of();

    final Dictionary dictionary;
    final MappingEntryRepository entries;
    final Pending pending;

    public String create(HttpRequest httpRequest) {
        return dictionary.define(proposal(), user(httpRequest)).getId();
    }

    /** An equivalence is never edited: saving approves the edited one as its next version. */
    public String save(HttpRequest httpRequest) {
        return dictionary.define(proposal(), user(httpRequest)).getId();
    }

    @Toolbar
    @Action
    public Object approve(HttpRequest httpRequest) {
        load(dictionary.approve(id, user(httpRequest)));
        return List.of(new Message("Approved: in force, and every process waiting for it resumes"), new State(this));
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "Reject this proposal?",
            confirmationMessage = "It stays in the history as rejected.")
    public Object reject(HttpRequest httpRequest) {
        load(dictionary.reject(id, user(httpRequest)));
        return List.of(new Message("Rejected"), new State(this));
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "Withdraw this equivalence?",
            confirmationMessage = "It stops translating the code, and nothing takes its place: what needs the code from now on waits until someone maps it again. What was already written in Opera is not changed. To correct an equivalence, define the right one instead — it replaces this one.")
    public Object withdraw(HttpRequest httpRequest) {
        load(dictionary.withdraw(id, user(httpRequest)));
        return List.of(new Message("Withdrawn: the code has no equivalence again"), new State(this));
    }

    private Dictionary.Proposal proposal() {
        var map = new LinkedHashMap<String, String>();
        if (attributes != null) {
            attributes.stream().filter(a -> a.name() != null && !a.name().isBlank()).forEach(a -> map.put(a.name(), a.value()));
        }
        return new Dictionary.Proposal(type, hotelCode, crsCode, pmsCode, map, null, null);
    }

    /** The console's user, as their token names them; "console" when there is none. */
    static String user(HttpRequest httpRequest) {
        return io.mateu.ecdemo1.mapping.ui.ConsoleUser.of(httpRequest);
    }

    /** A CRS code of the hotel that nothing translates yet: not an entry until it is saved. */
    public EntryViewModel loadUnmapped(String hotel, CodeType codeType, String code) {
        status = new Status(StatusType.DANGER, "Unmapped");
        type = codeType;
        hotelCode = hotel;
        crsCode = code;
        pmsCode = null;
        attributes = List.of();
        pmsCodes = offered(hotel, codeType);
        return this;
    }

    List<PmsCodeRow> offered(String hotel, CodeType codeType) {
        if (hotel == null) {
            return List.of();
        }
        try {
            return pending.pmsCatalog(hotel).stream().filter(c -> c.type() == codeType)
                    .map(c -> new PmsCodeRow(c.type().name(), c.code(), c.description())).toList();
        } catch (RuntimeException e) {
            return List.of(new PmsCodeRow(codeType.name(), "", "The PMS catalog could not be read: " + e.getMessage()));
        }
    }

    /** Each decision only where it applies; an unmapped code has none — it is mapped by saving it. */
    @Override
    public boolean isHidden(String memberName, HttpRequest httpRequest) {
        return switch (memberName) {
            case "approve", "reject" -> !"Proposed".equals(status.message());
            case "withdraw" -> !"Approved".equals(status.message());
            case "pmsCodes" -> pmsCodes == null || pmsCodes.isEmpty();
            default -> false;
        };
    }

    public EntryViewModel load(MappingEntry e) {
        status = DictionaryCrud.status(e.status);
        type = e.type;
        hotelCode = e.hotelCode;
        crsCode = e.sourceCode;
        pmsCode = e.targetCode;
        attributes = e.attributes == null ? List.of()
                : e.attributes.entrySet().stream().map(a -> new AttributeRow(a.getKey(), a.getValue())).toList();
        id = e.id;
        version = e.entryVersion;
        proposedBy = e.proposedBy;
        confidence = e.confidence;
        rationale = e.rationale;
        decided = e.decidedBy == null ? "" : e.decidedBy + " at " + e.decidedAt;
        pmsCodes = e.status == EntryStatus.PROPOSED ? offered(e.hotelCode, e.type) : List.of();
        return this;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return id != null ? type + " " + crsCode + " → " + pmsCode + " (" + (hotelCode == null ? "chain" : hotelCode) + ")"
                : "New equivalence";
    }
}
