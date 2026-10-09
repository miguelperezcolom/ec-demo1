package io.mateu.ecdemo1.loyalty.infra.in.ui.pages;

import io.mateu.ecdemo1.loyalty.store.Tier;
import io.mateu.uidl.annotations.Label;

import java.util.Set;

/** The search bar: the free text looks in the number and the customer code; the tier narrows. */
public class MemberFilters {

    // A Set: the bar offers an enum filter as a multi-select and sends a list.
    @Label("Nivel")
    public Set<Tier> tier;
}
