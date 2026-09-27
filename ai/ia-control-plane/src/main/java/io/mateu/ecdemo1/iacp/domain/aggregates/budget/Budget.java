package io.mateu.ecdemo1.iacp.domain.aggregates.budget;

import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetId;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetPeriod;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetScope;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Enabled;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Time;
import io.mateu.workflow.ddd.AggregateRoot;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A cap on token spend: how much of a {@code subject} may be used per {@code period} before the
 * control plane stops serving the agents that would spend it.
 *
 * <p>{@code subjectId} is the id the {@code scope} points at — a specific agent, model, user or
 * tenant — so a budget is always about one named thing. Limiting "everything" is not a special
 * subject here; it is a budget per agent, which keeps the check a lookup rather than a policy
 * language. The limit is a token count, the same unit the usage log records, so enforcement is a
 * comparison and not a conversion.
 *
 * <p>Like every catalogue entry it can be disabled rather than deleted, and a disabled budget stops
 * biting while keeping its history — the difference between lifting a cap for a day and forgetting
 * it existed.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class Budget extends AggregateRoot {

    BudgetId id;
    Name name;
    BudgetScope scope;
    /** The id of the agent / model / user / tenant this budget caps. */
    String subjectId;
    BudgetPeriod period;
    long limitTokens;
    Enabled enabled;
    Time created;

    public static Budget of(BudgetId id, Name name, BudgetScope scope, String subjectId,
                            BudgetPeriod period, long limitTokens) {
        var budget = new Budget();
        budget.id = id;
        budget.name = name;
        budget.scope = scope;
        budget.subjectId = subjectId;
        budget.period = period;
        budget.limitTokens = limitTokens;
        budget.enabled = Enabled.yes();
        budget.created = new Time(LocalDateTime.now());
        return budget;
    }

    public void update(Name name, BudgetScope scope, String subjectId, BudgetPeriod period,
                       long limitTokens, Enabled enabled) {
        this.name = name;
        this.scope = scope;
        this.subjectId = subjectId;
        this.period = period;
        this.limitTokens = limitTokens;
        this.enabled = enabled;
    }

    public boolean isUsable() {
        return enabled.value();
    }
}
