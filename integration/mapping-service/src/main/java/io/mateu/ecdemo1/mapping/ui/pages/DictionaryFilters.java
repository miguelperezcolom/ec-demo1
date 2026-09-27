package io.mateu.ecdemo1.mapping.ui.pages;

import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.mapping.ui.suppliers.IntegrationLabel;
import io.mateu.ecdemo1.mapping.ui.suppliers.IntegrationOptions;
import io.mateu.uidl.annotations.Lookup;

import java.util.Set;

/**
 * The dictionary's search bar. Choosing an integration narrows it to what applies to that hotel —
 * its own entries and the chain's — and adds what is still unmapped there: the CRS codes with no
 * equivalent, which is what the pending-mapping screen used to list.
 */
public class DictionaryFilters {

    /** UNMAPPED is not an entry: a CRS code of the chosen hotel that nothing translates yet. */
    public enum State { UNMAPPED, PROPOSED, APPROVED, REJECTED, SUPERSEDED, WITHDRAWN }

    /** The value is the CRS hotel, which is what the mapping is keyed by. */
    @Lookup(search = IntegrationOptions.class, label = IntegrationLabel.class)
    String integration;

    CodeType type;

    Set<State> status;
}
