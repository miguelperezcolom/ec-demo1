package io.mateu.ecdemo1.integration.model.customer;

/** Where a golden record is in its life (HLA CRM-MDM, «El golden record»). */
public enum CustomerStatus {
    /** Created from a reservation with no match: it serves the sale while cleaning is pending. */
    PROVISIONAL,
    /** It survived a merge: cleaning found others to be the same customer. */
    CONSOLIDATED,
    /** Absorbed by a merge: an alias that resolves to its survivor, kept so nothing points nowhere. */
    MERGED
}
