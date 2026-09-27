package io.mateu.ecdemo1.erp.infra.in.ui.pages;

import java.util.Set;
import io.mateu.ecdemo1.erp.domain.partner.BillingMode;
import io.mateu.ecdemo1.erp.domain.partner.PartnerType;

/** Each field is a filter in the search bar; together with the free text they narrow the partners. */
public class PartnerFilters {

    public enum PartnerStatus { Active, Inactive }

    // Sets, because the bar offers every enum filter as a multi-select: it sends a list, and a single
    // enum here read that list as nothing — the partners came back unfiltered.
    Set<PartnerType> type;
    Set<BillingMode> billingMode;
    Set<PartnerStatus> status;
}
