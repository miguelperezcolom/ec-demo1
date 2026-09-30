package io.mateu.ecdemo1.registration.infra.in.ui.pages;

import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Scope;
import io.mateu.uidl.annotations.Label;

import java.util.Set;

/** The search bar: each field narrows the rules, with the free text (name, country or hotel, legal basis). */
public class RuleFilters {

    public enum State { Activa, Inactiva }

    @Label("Ámbito")
    Set<Scope> scope;

    @Label("Estado")
    Set<State> status;
}
