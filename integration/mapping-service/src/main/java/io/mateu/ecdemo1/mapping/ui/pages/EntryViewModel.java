package io.mateu.ecdemo1.mapping.ui.pages;

import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.mapping.dictionary.Dictionary;
import io.mateu.ecdemo1.mapping.dictionary.Pending;
import io.mateu.ecdemo1.mapping.store.EntryStatus;
import io.mateu.ecdemo1.mapping.store.MappingEntry;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.EditableOnlyWhenCreating;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.MasterDetail;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.State;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import io.mateu.uidl.interfaces.OptionsSupplier;
import io.mateu.uidl.interfaces.StereotypeSupplier;
import io.mateu.uidl.interfaces.VisibilitySupplier;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
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
 *
 * <p>Once it exists — an entry, or an unmapped code — only its PMS side is to decide: the PMS code
 * (and a channel's market code) is a select of what the PMS offers for that type at that hotel —
 * at every integrated property, for a chain-level one — and the rest is read-only. Created by hand,
 * the type, hotel and CRS code are chosen too, and the PMS code is typed.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class EntryViewModel implements Identifiable, VisibilitySupplier, OptionsSupplier, StereotypeSupplier {

    static final String MARKET_CODE = "marketCode";

    @ReadOnly
    @HiddenInCreate
    Status status = new Status(StatusType.NONE, "New");

    @Section("Equivalence")
    @NotNull
    @EditableOnlyWhenCreating
    CodeType type;
    /** Empty: chain-level. */
    @EditableOnlyWhenCreating
    String hotelCode;
    @NotEmpty
    @EditableOnlyWhenCreating
    String crsCode;
    /** Once the entry exists, the only thing to decide: a select of what the PMS offers (see {@link #stereotype}). */
    @NotEmpty
    String pmsCode;
    /** A CHANNEL is, in Opera, a source code and a market code: the second one, chosen the same way. */
    String marketCode;
    @MasterDetail(minHeightWhenDetailVisible = "14rem;")
    @Colspan(2)
    @EditableOnlyWhenCreating
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
    final Pending pending;

    /** What the PMS offers for the type (and for MARKET), read once per request; never part of the state. */
    private transient List<Pending.PmsCode> offer;
    private transient List<Pending.PmsCode> markets;

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
        if (marketCode != null && !marketCode.isBlank()) {
            map.put(MARKET_CODE, marketCode);
        }
        return new Dictionary.Proposal(type, hotelCode, crsCode, pmsCode, map, null, null);
    }

    /** The console's user, as their token names them; "console" when there is none. */
    static String user(HttpRequest httpRequest) {
        return io.mateu.ecdemo1.uicommons.user.ConsoleUser.of(httpRequest);
    }

    /** A CRS code of the hotel that nothing translates yet: not an entry until it is saved. */
    public EntryViewModel loadUnmapped(String hotel, CodeType codeType, String code) {
        status = new Status(StatusType.DANGER, "Unmapped");
        type = codeType;
        hotelCode = hotel;
        crsCode = code;
        pmsCode = null;
        marketCode = null;
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
            // Created by hand, a channel's market code goes among the attributes, as any other.
            case "marketCode" -> creating() || type != CodeType.CHANNEL;
            default -> false;
        };
    }

    public EntryViewModel load(MappingEntry e) {
        var attrs = e.getAttributes();
        status = DictionaryCrud.status(e.getStatus());
        type = e.getType();
        hotelCode = e.getHotelCode();
        crsCode = e.getSourceCode();
        pmsCode = e.getTargetCode();
        marketCode = e.getType() == CodeType.CHANNEL && attrs != null ? attrs.get(MARKET_CODE) : null;
        attributes = attrs == null ? List.of()
                : attrs.entrySet().stream()
                        .filter(a -> e.getType() != CodeType.CHANNEL || !MARKET_CODE.equals(a.getKey()))
                        .map(a -> new AttributeRow(a.getKey(), a.getValue())).toList();
        id = e.getId();
        version = e.getEntryVersion();
        proposedBy = e.getProposedBy();
        confidence = e.getConfidence();
        rationale = e.getRationale();
        decided = e.getDecidedBy() == null ? "" : e.getDecidedBy() + " at " + e.getDecidedAt();
        pmsCodes = e.getStatus() == EntryStatus.PROPOSED ? offered(e.getHotelCode(), e.getType()) : List.of();
        return this;
    }

    /**
     * A new equivalence, typed by hand: everything is still to be chosen, the type and hotel the
     * PMS's codes depend on included. An entry, or an unmapped code, has those fixed.
     */
    boolean creating() {
        return id == null && (status == null || !"Unmapped".equals(status.message()));
    }

    /** The PMS code — and a channel's market code — as a select of what the PMS offers, once there is something to offer. */
    @Override
    public FieldStereotype stereotype(String memberName, HttpRequest httpRequest) {
        return switch (memberName) {
            case "pmsCode", "marketCode" -> !creating() && !options(memberName, httpRequest).isEmpty()
                    ? FieldStereotype.select : null;
            default -> null;
        };
    }

    /** The select's choices apply to this form's two PMS-side fields; nothing else — not the grids' columns. */
    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return EntryViewModel.class.equals(formType) && ("pmsCode".equals(fieldName) || "marketCode".equals(fieldName));
    }

    /**
     * Supplying options replaces the enum's own, so the type keeps them here. The PMS's codes are
     * labelled "CODE — name"; the value in force stays among them even if the PMS no longer has it.
     */
    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return switch (fieldName) {
            case "type" -> Arrays.stream(CodeType.values()).map(t -> new Option(t.name(), t.name())).toList();
            case "pmsCode" -> choices(pmsCode, offer());
            case "marketCode" -> type == CodeType.CHANNEL ? choices(marketCode, markets()) : List.of();
            default -> List.of();
        };
    }

    static List<Option> choices(String current, List<Pending.PmsCode> codes) {
        if (codes.isEmpty()) {
            return List.of();
        }
        var options = new ArrayList<Option>();
        if (current != null && !current.isBlank() && codes.stream().noneMatch(c -> current.equals(c.code()))) {
            options.add(new Option(current, current + " — not in the PMS catalog"));
        }
        codes.forEach(c -> options.add(new Option(c.code(), label(c))));
        return options;
    }

    static String label(Pending.PmsCode c) {
        var name = c.description() == null || c.description().isBlank() ? "" : " — " + c.description();
        var where = c.properties().isEmpty() ? "" : " (only " + String.join(", ", c.properties()) + ")";
        return c.code() + name + where;
    }

    List<Pending.PmsCode> offer() {
        if (offer == null) {
            offer = read(type);
        }
        return offer;
    }

    List<Pending.PmsCode> markets() {
        if (markets == null) {
            markets = read(CodeType.MARKET);
        }
        return markets;
    }

    /** Unreadable, the PMS offers nothing: the field stays a text field rather than an empty select. */
    List<Pending.PmsCode> read(CodeType codeType) {
        try {
            return pending.pmsCodes(hotelCode, codeType);
        } catch (RuntimeException e) {
            return List.of();
        }
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
