package io.mateu.ecdemo1.audit.ui.pages;

import io.mateu.uidl.data.DateRange;

import java.util.Set;

/** Each field is a filter in the search bar; together with the free text they narrow the trail. */
public class AuditFilters {

    public enum Outcome { CARRIED_OUT, REFUSED }

    DateRange when;
    String hotel;
    String user;
    String action;
    String service;
    Set<Outcome> outcome;
}
