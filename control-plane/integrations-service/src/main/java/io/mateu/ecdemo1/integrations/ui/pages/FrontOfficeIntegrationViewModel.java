package io.mateu.ecdemo1.integrations.ui.pages;

import io.mateu.ecdemo1.integrations.application.FrontOfficeIntegrationQueries;
import io.mateu.ecdemo1.integrations.config.IntegrationsProperties;
import io.mateu.ecdemo1.integrations.frontoffice.FoPolling;
import io.mateu.ecdemo1.integrations.frontoffice.FrontOfficeIntegrations;
import io.mateu.ecdemo1.integrations.rest.FrontOfficeIntegrationDto;
import io.mateu.ecdemo1.integrations.store.FrontOfficeIntegration;
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
 * A hotel's front office fed from its PMS: which property, which front office, which of the
 * property's reservations and how far ahead — and where its onboarding and its polling of the PMS
 * are. Every action is a person's decision and is recorded with their name. What is set when it is
 * registered stays: another front office, or another scope, is another integration.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class FrontOfficeIntegrationViewModel implements Identifiable {

    @ReadOnly
    @HiddenInCreate
    Status status = new Status(StatusType.NONE, "New");

    /** The chain's properties, as Opera lists them for the chain's connection. */
    @Section("Front office")
    @NotEmpty
    @EditableOnlyWhenCreating
    @Lookup(search = OperaPropertyOptions.class, label = OperaPropertyLabel.class)
    String operaProperty;
    /** The front office's hotel, as it names itself. */
    @NotEmpty
    @EditableOnlyWhenCreating
    String frontOffice;
    String name;
    /** Where the front office answers the integration's queries; the stays and the catalogue go by Kafka. */
    @EditableOnlyWhenCreating
    String frontOfficeUrl;
    /**
     * CHAIN (the default): only those the chain's integration wrote. ALL: every reservation of the
     * property, wherever it was made — in a shared Opera tenant, real guests of others too.
     */
    @EditableOnlyWhenCreating
    FrontOfficeIntegration.Scope scope = FrontOfficeIntegration.Scope.DEFAULT;
    /** How many days ahead of today the front office is backfilled, and polled for changes. */
    @EditableOnlyWhenCreating
    int horizonDays;

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
    @Colspan(2)
    String catalogue;
    @ReadOnly
    @HiddenInCreate
    @Colspan(2)
    String backfill;

    @Section("Changes from the PMS")
    @ReadOnly
    @HiddenInCreate
    String pollCursor;
    @ReadOnly
    @HiddenInCreate
    String lastPoll;

    @Section("History")
    @ReadOnly
    @HiddenInCreate
    @Stereotype(FieldStereotype.grid)
    List<HistoryRow> history;

    @ReadOnly
    @HiddenInCreate
    String id;

    final FrontOfficeIntegrations lifecycle;
    final FoPolling polling;
    final FrontOfficeIntegrationQueries queries;
    final IntegrationsProperties properties;

    public FrontOfficeIntegrationViewModel blank() {
        status = new Status(StatusType.NONE, "New");
        operaProperty = null;
        frontOffice = null;
        name = null;
        frontOfficeUrl = properties.frontOfficeUrl();
        scope = FrontOfficeIntegration.Scope.DEFAULT;
        horizonDays = properties.frontOffice().horizonDays();
        id = null;
        return this;
    }

    public String create(HttpRequest httpRequest) {
        return lifecycle.register(new FrontOfficeIntegrations.Registration(operaProperty, frontOffice, name, frontOfficeUrl,
                scope, horizonDays), user(httpRequest)).pmsHotelCode;
    }

    /** Nothing of it is edited after registering: saving only looks again. */
    public String save(HttpRequest httpRequest) {
        lifecycle.recheck(id, user(httpRequest));
        return operaProperty;
    }

    @Toolbar
    @Action
    public Object verify(HttpRequest httpRequest) {
        return act(httpRequest, "Connections tried", i -> lifecycle.verifyNow(i, user(httpRequest)));
    }

    @Toolbar
    @Action
    public Object recheck(HttpRequest httpRequest) {
        return act(httpRequest, "Looked again", i -> lifecycle.recheck(i, user(httpRequest)));
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "Send the PMS's catalogue again?",
            confirmationMessage = "Opera's room types, rate plans, packages and rooms are read again and replace what the front office has.")
    public Object resyncCatalogue(HttpRequest httpRequest) {
        return act(httpRequest, "Catalogue sent", i -> lifecycle.resyncCatalogue(i, user(httpRequest)));
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "Activate this integration?",
            confirmationMessage = "Every change of the property in the PMS reaches the front office: as it is written, and by polling Opera.")
    public Object activate(HttpRequest httpRequest) {
        return act(httpRequest, "Activation requested", i -> lifecycle.activate(i, user(httpRequest)));
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "Pause this integration?",
            confirmationMessage = "Nothing reaches the front office until it is resumed; the polling then brings what changed meanwhile.")
    public Object pause(HttpRequest httpRequest) {
        return act(httpRequest, "Paused", i -> lifecycle.pause(i, user(httpRequest)));
    }

    @Toolbar
    @Action
    public Object resume(HttpRequest httpRequest) {
        return act(httpRequest, "Resumed", i -> lifecycle.resume(i, user(httpRequest)));
    }

    @Toolbar
    @Action
    public Object pollNow(HttpRequest httpRequest) {
        return act(httpRequest, "Opera asked for what changed", i -> polling.pollNow(i, user(httpRequest)));
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "Relaunch the backfill?",
            confirmationMessage = "The property's reservations of the horizon are projected into the front office again. Nothing is duplicated.")
    public Object relaunchBackfill(HttpRequest httpRequest) {
        return act(httpRequest, "Backfill relaunched", i -> {
            lifecycle.relaunchBackfill(i, user(httpRequest));
            return lifecycle.find(i);
        });
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "Decommission this integration?",
            confirmationMessage = "Nothing more of the PMS reaches the front office. What already did stays there.")
    public Object decommission(HttpRequest httpRequest) {
        return act(httpRequest, "Decommissioned", i -> lifecycle.decommission(i, user(httpRequest)));
    }

    Object act(HttpRequest httpRequest, String done, Function<String, FrontOfficeIntegration> action) {
        load(action.apply(id));
        return List.of(new Message(done), new State(this));
    }

    static String user(HttpRequest httpRequest) {
        return io.mateu.ecdemo1.uicommons.user.ConsoleUser.of(httpRequest);
    }

    public FrontOfficeIntegrationViewModel load(FrontOfficeIntegration i) {
        var run = queries.lastBackfill(i.id).orElse(null);
        status = FrontOfficeIntegrationCrud.status(i.getStatus());
        operaProperty = i.pmsHotelCode;
        frontOffice = i.frontOfficeCode;
        name = i.name;
        frontOfficeUrl = i.frontOfficeUrl;
        scope = i.scope;
        horizonDays = i.horizonDays;
        var gate = FrontOfficeIntegrationDto.waitingFor(i.gate);
        waitingFor = gate == null ? "Nothing" : gate;
        connectivity = i.connectivityOk == null ? "Not tried yet"
                : (i.connectivityOk ? "OK — " : "FAILED — ") + i.connectivityMessage + " (" + i.connectivityCheckedAt + ")";
        catalogue = i.catalogueCommandId == null ? "Not sent yet"
                : i.catalogueSyncedAt == null ? "Sent at " + i.catalogueRequestedAt + "; the front office has not taken it yet"
                : i.catalogueSummary + " (held since " + i.catalogueSyncedAt + ")";
        backfill = run == null ? "Not started" : "%s — %d%s projected%s".formatted(run.status, run.dispatched,
                run.expected == null ? "" : " of " + run.expected, run.onboarding ? " (onboarding)" : "");
        pollCursor = i.pollCursor == null ? "Not polled yet" : "Opera's changes up to " + i.pollCursor;
        lastPoll = i.lastPollAt == null ? "Never" : "%s — %d reservation(s) changed".formatted(i.lastPollAt,
                i.lastPollChanges == null ? 0 : i.lastPollChanges);
        history = i.history == null ? List.of() : i.history.reversed().stream()
                .map(h -> new HistoryRow(String.valueOf(h.at()), h.by(), h.what())).toList();
        id = i.id;
        return this;
    }

    @Override
    public String id() {
        return operaProperty;
    }

    @Override
    public String toString() {
        return id != null ? operaProperty + " → " + frontOffice : "New front office integration";
    }
}
