package io.mateu.ecdemo1.mapping.ui.pages;

import io.mateu.ecdemo1.mapping.dictionary.Pending;
import io.mateu.ecdemo1.mapping.proposals.AgentProposals;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Button;
import io.mateu.uidl.annotations.Notice;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.ButtonStyle;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.State;
import io.mateu.uidl.interfaces.HttpRequest;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * What is left to map for a hotel, next to what the PMS offers — and the button that asks the
 * agent to propose it. The agent's proposal lands in the dictionary as proposals: nothing here
 * puts anything in force.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Pending mapping")
public class PendingPage {

    @Section("Hotel")
    @NotEmpty
    String hotelCode;

    @Notice
    String notice;

    @Section("CRS codes with no approved equivalent")
    @ReadOnly
    @Stereotype(FieldStereotype.grid)
    List<PendingCodeRow> pending;

    @Section("PMS codes for this hotel")
    @ReadOnly
    @Stereotype(FieldStereotype.grid)
    List<PmsCodeRow> pmsCodes;

    final Pending queries;
    final AgentProposals agent;

    @Button(buttonStyle = ButtonStyle.primary)
    @Action(validationRequired = true)
    public Object load(HttpRequest httpRequest) {
        pending = queries.pendingCodes(hotelCode).stream()
                .map(p -> new PendingCodeRow(p.type().name(), p.code(), p.description(), p.proposed() ? "proposed" : ""))
                .toList();
        try {
            pmsCodes = queries.pmsCatalog(hotelCode).stream()
                    .map(c -> new PmsCodeRow(c.type().name(), c.code(), c.description())).toList();
            notice = pending.isEmpty() ? "Nothing pending for " + hotelCode : null;
        } catch (RuntimeException e) {
            pmsCodes = List.of();
            notice = "The PMS catalog could not be read: " + e.getMessage();
        }
        return new State(this);
    }

    @Toolbar
    @Action(validationRequired = true)
    public Object askTheAgent(HttpRequest httpRequest) {
        var answer = agent.requestProposal(hotelCode, httpRequest.getHeaderValue("Authorization"));
        notice = answer;
        return List.of(new Message("The agent answered; its proposals are in the dictionary, waiting for review"),
                new State(this));
    }
}
