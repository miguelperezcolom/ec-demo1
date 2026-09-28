package io.mateu.ecdemo1.integrations.usage;

import io.mateu.ecdemo1.integration.model.usage.ApiUsage;

/**
 * How worrying an API's usage is, for the page's pause state and colours. Salesforce: from 80% of the
 * org's daily allowance a warning, from 95% — or while the calls are paused because it is spent — an
 * error. Opera has no allowance it tells: a 429 in the last 24 hours is the warning.
 */
public enum UsageLevel {
    OK, WARNING, ERROR, UNKNOWN;

    static final double WARNING_AT = 0.80;
    static final double ERROR_AT = 0.95;

    public static UsageLevel of(ApiUsage usage) {
        if (usage == null) {
            return UNKNOWN;
        }
        if (usage.paused()) {
            return ERROR;
        }
        if (usage.orgUsed() != null && usage.orgMax() != null && usage.orgMax() > 0) {
            var ratio = (double) usage.orgUsed() / usage.orgMax();
            return ratio >= ERROR_AT ? ERROR : ratio >= WARNING_AT ? WARNING : usage.limited24h() > 0 ? WARNING : OK;
        }
        if (usage.limited24h() > 0) {
            return WARNING;
        }
        return "salesforce".equals(usage.api()) && usage.orgUsed() == null ? UNKNOWN : OK;
    }

    /** The colour, from the theme: the Vaadin shells' Lumo variables, with a plain fallback. */
    public String color() {
        return switch (this) {
            case OK -> "inherit";
            case WARNING -> "var(--lumo-warning-text-color, #a35a00)";
            case ERROR -> "var(--lumo-error-text-color, #c2182b)";
            case UNKNOWN -> "var(--lumo-secondary-text-color, #6b7280)";
        };
    }

    /** As a Mateu status/theme name. */
    public String theme() {
        return switch (this) {
            case OK -> "success";
            case WARNING -> "warning";
            case ERROR -> "error";
            case UNKNOWN -> "contrast";
        };
    }
}
