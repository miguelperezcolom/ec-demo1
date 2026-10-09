package io.mateu.ecdemo1.mdm.ui.pages;

import io.mateu.ecdemo1.mdm.application.ConsolidationQueries.Propagation;

import java.util.Set;

/** The consolidations' filter bar. Empty keeps every value. */
public class ConsolidationFilters {

    /**
     * Who noticed the consolidation first: Salesforce's Pub/Sub event, the poll that backs it up, or
     * the MDM itself, when a scanned document already belonged to another customer (SCAN) or the desk
     * confirmed a pax is a known customer (DESK_CONFIRMED).
     */
    public enum Via { EVENT, POLL, SCAN, DESK_CONFIRMED }

    Set<Via> via;

    Set<Propagation> propagation;
}
