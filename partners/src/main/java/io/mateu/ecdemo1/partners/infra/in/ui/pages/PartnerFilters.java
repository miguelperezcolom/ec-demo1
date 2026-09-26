package io.mateu.ecdemo1.partners.infra.in.ui.pages;

import io.mateu.ecdemo1.partners.domain.partner.BillingMode;
import io.mateu.ecdemo1.partners.domain.partner.PartnerType;

/** Each field is a filter in the search bar; together with the free text they narrow the partners. */
public class PartnerFilters {

    public enum PartnerStatus { Active, Inactive }

    PartnerType type;
    BillingMode billingMode;
    PartnerStatus status;
}
