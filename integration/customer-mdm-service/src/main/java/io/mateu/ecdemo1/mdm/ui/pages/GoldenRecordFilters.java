package io.mateu.ecdemo1.mdm.ui.pages;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.store.SalesforceState;

import java.util.Set;

/** The golden records' filter bar: the two columns worth narrowing a long list by. Empty keeps every value. */
public class GoldenRecordFilters {

    /**
     * A golden record's status. Not {@link CustomerStatus} itself: MERGED would be offered and never
     * match — a merged customer is an alias, and the listing leaves aliases out.
     */
    public enum Status {
        PROVISIONAL, CONSOLIDATED;

        CustomerStatus customerStatus() {
            return CustomerStatus.valueOf(name());
        }
    }

    Set<SalesforceState> salesforce;

    Set<Status> status;
}
