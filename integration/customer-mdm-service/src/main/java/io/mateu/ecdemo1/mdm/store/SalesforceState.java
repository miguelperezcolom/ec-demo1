package io.mateu.ecdemo1.mdm.store;

/** Where a customer stands with Salesforce, which cleans it but does not own it. */
public enum SalesforceState {
    /** To be sent — new, or changed since it was. */
    PENDING,
    /** Salesforce has the current version, as a contact. */
    PROJECTED,
    /** Salesforce refused it, and would again: sent once more when it changes. */
    FAILED,
    /** Someone deleted its contact in Salesforce without merging it: not sent again on its own. */
    REMOVED,
    /** Not needed there: absorbed by a merge. */
    NOT_PROJECTED
}
