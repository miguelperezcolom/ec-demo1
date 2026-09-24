package io.mateu.ecdemo1.integrations.ui.pages;

import io.mateu.ecdemo1.integrations.lifecycle.Integrations;
import io.mateu.ecdemo1.integrations.rest.IntegrationDto;
import io.mateu.ecdemo1.integrations.store.BackfillRunRepository;
import io.mateu.ecdemo1.integrations.store.Integration;
import io.mateu.ecdemo1.integrations.ui.suppliers.CrsHotelLabel;
import io.mateu.ecdemo1.integrations.ui.suppliers.CrsHotelOptions;
import io.mateu.ecdemo1.integrations.ui.suppliers.OperaPropertyLabel;
import io.mateu.ecdemo1.integrations.ui.suppliers.OperaPropertyOptions;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.EditableOnlyWhenCreating;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.Lookup;
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
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Function;

/**
 * A hotel's integration: its connection to Opera, and where its onboarding is. The secret is
 * write-only — it is never shown, and leaving it blank when editing keeps the stored one. Every
 * action is a person's decision and is recorded with their name.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class IntegrationViewModel implements Identifiable {

    @ReadOnly
    @HiddenInCreate
    Status status = new Status(StatusType.NONE, "New");

    /** The CRS's hotels, from the CRS — not typed. */
    @Section("Hotel")
    @NotEmpty
    @EditableOnlyWhenCreating
    @Lookup(search = CrsHotelOptions.class, label = CrsHotelLabel.class)
    String crsHotelCode;
    /** The chain's properties, as Opera lists them for the chain's connection. */
    @NotEmpty
    @EditableOnlyWhenCreating
    @Lookup(search = OperaPropertyOptions.class, label = OperaPropertyLabel.class)
    String operaProperty;
    String name;

    /**
     * Filled in from the chain's connection: every property of the chain lives in the same tenant
     * (R24). Changing it here makes it this hotel's own.
     */
    @Section("Connection to Opera (OHIP)")
    @NotEmpty
    String gatewayUrl;
    @NotEmpty
    String appKey;
    @NotEmpty
    String clientId;
    /** Write-only: blank is the chain's secret when creating, and the stored one when editing. */
    @Stereotype(FieldStereotype.password)
    String clientSecret;
    @NotEmpty
    String enterpriseId;

    @Section("Onboarding")
    @ReadOnly
    @HiddenInCreate
    String waitingFor;
    @ReadOnly
    @HiddenInCreate
    @Stereotype(FieldStereotype.textarea)
    @Colspan(2)
    String connectivity;
    @ReadOnly
    @HiddenInCreate
    @Stereotype(FieldStereotype.textarea)
    @Colspan(2)
    String catalogues;
    @ReadOnly
    @HiddenInCreate
    String mapping;
    @ReadOnly
    @HiddenInCreate
    String partners;
    @ReadOnly
    @HiddenInCreate
    String backfill;
    @ReadOnly
    @HiddenInCreate
    String availability;

    @Section("Backfill gaps")
    @ReadOnly
    @HiddenInCreate
    @Stereotype(FieldStereotype.grid)
    List<GapRow> gaps;

    @Section("History")
    @ReadOnly
    @HiddenInCreate
    @Stereotype(FieldStereotype.grid)
    List<HistoryRow> history;

    /** The integration's own id, kept for the actions; the screens are addressed by the hotel. */
    @ReadOnly
    @HiddenInCreate
    String id;

    final Integrations lifecycle;
    final BackfillRunRepository runs;

    /** A new integration starts from the chain's connection: the person names the hotel and the property. */
    public IntegrationViewModel blank() {
        var chain = lifecycle.chainConnection();
        status = new Status(StatusType.NONE, "New");
        crsHotelCode = null;
        operaProperty = null;
        name = null;
        gatewayUrl = chain.gatewayUrl();
        appKey = chain.appKey();
        clientId = chain.clientId();
        clientSecret = "";
        enterpriseId = chain.enterpriseId();
        id = null;
        return this;
    }

    public String create(HttpRequest httpRequest) {
        return lifecycle.register(new Integrations.Registration(crsHotelCode, operaProperty, name, gatewayUrl, appKey,
                clientId, clientSecret, enterpriseId), user(httpRequest)).crsHotelCode;
    }

    /** Saving changes the connection — and tries it at once. */
    public String save(HttpRequest httpRequest) {
        lifecycle.changeConnection(id, new Integrations.ConnectionChange(gatewayUrl, appKey, clientId, clientSecret,
                enterpriseId), user(httpRequest));
        return crsHotelCode;
    }

    @Toolbar
    @Action
    public Object verify(HttpRequest httpRequest) {
        return act(httpRequest, "Connection tried", i -> lifecycle.verifyNow(i, user(httpRequest)));
    }

    @Toolbar
    @Action
    public Object recheck(HttpRequest httpRequest) {
        return act(httpRequest, "Looked again", i -> lifecycle.recheck(i, user(httpRequest)));
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "Import the partners from Opera?",
            confirmationMessage = "The chain's agencies, companies and sources in Opera are created or brought up to date in the ERP. Nothing is written to Opera.")
    public Object importPartners(HttpRequest httpRequest) {
        return act(httpRequest, "Partners imported", i -> lifecycle.importPartners(i, user(httpRequest)));
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "Approve the mapping?",
            confirmationMessage = "The onboarding goes on to the partners and the backfill. Codes the reservations use and still lack will stop the backfill.")
    public Object approveMapping(HttpRequest httpRequest) {
        return act(httpRequest, "Mapping approved", i -> lifecycle.approveMapping(i, user(httpRequest)));
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "Activate this integration?",
            confirmationMessage = "Real-time traffic of the hotel flows to Opera, starting with what waited for this.")
    public Object activate(HttpRequest httpRequest) {
        return act(httpRequest, "Activation requested", i -> lifecycle.activate(i, user(httpRequest)));
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "Pause this integration?",
            confirmationMessage = "The hotel's reservations wait until it is resumed; none is lost.")
    public Object pause(HttpRequest httpRequest) {
        return act(httpRequest, "Paused", i -> lifecycle.pause(i, user(httpRequest)));
    }

    @Toolbar
    @Action
    public Object resume(HttpRequest httpRequest) {
        return act(httpRequest, "Resumed: what waited flows now", i -> lifecycle.resume(i, user(httpRequest)));
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "Relaunch the backfill?",
            confirmationMessage = "Every future reservation of the hotel is projected again, nearest arrival first. Nothing is duplicated.")
    public Object relaunchBackfill(HttpRequest httpRequest) {
        return act(httpRequest, "Backfill relaunched", i -> {
            lifecycle.relaunchBackfill(i, user(httpRequest));
            return lifecycle.find(i);
        });
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "Decommission this integration?",
            confirmationMessage = "Nothing more of this hotel reaches Opera. What already did stays there.")
    public Object decommission(HttpRequest httpRequest) {
        return act(httpRequest, "Decommissioned", i -> lifecycle.decommission(i, user(httpRequest)));
    }

    Object act(HttpRequest httpRequest, String done, Function<String, Integration> action) {
        load(action.apply(id));
        return List.of(new Message(done), new State(this));
    }

    /** The console's user, as their token names them; "console" when there is none. */
    static String user(HttpRequest httpRequest) {
        return io.mateu.ecdemo1.integrations.ui.ConsoleUser.of(httpRequest);
    }

    public IntegrationViewModel load(Integration i) {
        var run = runs.findFirstByIntegrationIdOrderByStartedAtDesc(i.id).orElse(null);
        status = IntegrationCrud.status(i.status);
        crsHotelCode = i.crsHotelCode;
        operaProperty = i.pmsHotelCode;
        name = i.name;
        gatewayUrl = i.gatewayUrl;
        appKey = i.appKey;
        clientId = i.clientId;
        clientSecret = "";
        enterpriseId = i.enterpriseId;
        waitingFor = orNothing(IntegrationDto.waitingFor(i.gate));
        connectivity = i.connectivityOk == null ? "Not tried yet"
                : (i.connectivityOk ? "OK — " : "FAILED — ") + i.connectivityMessage + " (" + i.connectivityCheckedAt + ")";
        catalogues = i.contrastSummary == null ? "Not contrasted yet" : i.contrastSummary;
        mapping = i.mappingApprovedAt == null ? orNothing(i.pendingMappings == null ? null : i.pendingMappings + " code(s) pending")
                : "Approved by " + i.mappingApprovedBy + " at " + i.mappingApprovedAt;
        partners = i.partnersMissing == null || i.partnersMissing.isEmpty() ? "Every active partner is a PMS profile"
                : "Not PMS profiles yet: " + String.join(", ", i.partnersMissing);
        backfill = run == null ? "Not started" : "%s — %d%s projected, window to %s %s".formatted(run.status, run.dispatched,
                run.expected == null ? "" : " of " + run.expected, run.windowEnd, run.windowCovered ? "(covered)" : "");
        availability = i.availabilitySuspendedSince != null ? "Suspended since " + i.availabilitySuspendedSince
                : i.availabilityResyncedAt != null ? "Read in full at " + i.availabilityResyncedAt : "Normal";
        gaps = i.gaps == null ? List.of() : i.gaps.stream().map(g -> new GapRow(g.kind(), g.type(), g.code(), g.reservations())).toList();
        history = i.history == null ? List.of() : i.history.reversed().stream()
                .map(h -> new HistoryRow(String.valueOf(h.at()), h.by(), h.what())).toList();
        id = i.id;
        return this;
    }

    static String orNothing(String value) {
        return value == null ? "Nothing" : value;
    }

    /** The hotel, not the uuid: one integration per hotel, and it reads better in a URL. */
    @Override
    public String id() {
        return crsHotelCode;
    }

    @Override
    public String toString() {
        return id != null ? crsHotelCode + " ↔ " + operaProperty : "New integration";
    }
}
