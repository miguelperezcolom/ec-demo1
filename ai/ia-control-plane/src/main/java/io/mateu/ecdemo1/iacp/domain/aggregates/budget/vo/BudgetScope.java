package io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo;

/**
 * What a budget is charged against. The subject the limit follows: one agent's spend, one model's,
 * one person's, or one tenant's. A resolve carries all four, and every budget whose scope matches a
 * subject present is checked — so a per-user cap and a per-agent cap can both apply to the same
 * prompt, and the tighter one bites first.
 */
public enum BudgetScope {
    AGENT,
    LLM,
    USER,
    TENANT
}
