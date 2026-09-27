package io.mateu.ecdemo1.iacp.infra.in.ui.pages.budget;

import io.mateu.ecdemo1.iacp.application.out.query.dto.BudgetDto;
import io.mateu.ecdemo1.iacp.application.usecases.budget.create.CreateBudgetCommand;
import io.mateu.ecdemo1.iacp.application.usecases.budget.create.CreateBudgetUseCase;
import io.mateu.ecdemo1.iacp.application.usecases.budget.update.UpdateBudgetCommand;
import io.mateu.ecdemo1.iacp.application.usecases.budget.update.UpdateBudgetUseCase;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetPeriod;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetScope;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.HiddenInList;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * The editor for a spend cap.
 *
 * <p>{@code subjectId} is what the {@code scope} points at — an agent id, a model id, a username, a
 * tenant. It is free text because the thing it names lives in another catalogue (or in Keycloak, for
 * a user), and validating it here would couple this editor to all of them; a subject that matches
 * nothing simply never has spend to cap, which is a harmless budget, not a broken one.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class BudgetViewModel implements Identifiable {

    @Section("Budget")
    @ReadOnly
    @HiddenInCreate
    @Help("Cannot be changed once created.")
    // Deliberately not @NotEmpty — see the note on LlmViewModel.id. The requirement belongs on
    // newId, which is the field the creation form actually renders.
    String id;

    @NotEmpty
    @HiddenInList
    @Help("Only used when creating. Lowercase, no spaces — e.g. daily-per-user.")
    String newId;

    @NotEmpty
    String name;

    @NotNull
    @Help("What the cap follows: one AGENT, one LLM, one USER or one TENANT.")
    BudgetScope scope;

    @NotEmpty
    @Help("The id of the agent / model / user / tenant this caps — the subject of the scope above.")
    String subjectId;

    @NotNull
    @Help("The window the limit applies over. Resets at midnight (DAY) or the 1st (MONTH), UTC.")
    BudgetPeriod period;

    @Help("The most tokens the subject may spend in a window. The last prompt that crosses it is "
            + "served; the next is refused until the window resets.")
    long limitTokens;

    @Section("Status")
    boolean enabled;

    final CreateBudgetUseCase createBudgetUseCase;
    final UpdateBudgetUseCase updateBudgetUseCase;

    public String create(HttpRequest httpRequest) {
        return createBudgetUseCase.handle(new CreateBudgetCommand(newId, name, scope, subjectId,
                period, limitTokens));
    }

    public void save(HttpRequest httpRequest) {
        updateBudgetUseCase.handle(new UpdateBudgetCommand(id, name, scope, subjectId, period,
                limitTokens, enabled));
    }

    @Override
    public String id() {
        return id;
    }

    public BudgetViewModel load(BudgetDto dto) {
        id = dto.id();
        newId = dto.id();
        name = dto.name();
        scope = dto.scope();
        subjectId = dto.subjectId();
        period = dto.period();
        limitTokens = dto.limitTokens();
        enabled = dto.enabled();
        return this;
    }

    @Override
    public String toString() {
        return id != null ? name : "New budget";
    }
}
