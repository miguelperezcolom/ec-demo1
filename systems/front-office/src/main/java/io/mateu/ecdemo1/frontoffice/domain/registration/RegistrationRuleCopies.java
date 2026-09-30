package io.mateu.ecdemo1.frontoffice.domain.registration;

import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged;
import java.util.List;

/** This front office's copy of the registration rules: the last version of each, as the control plane sent it. */
public interface RegistrationRuleCopies {

  /** Keeps the rule unless the one kept is this version or a newer one; whether it was kept. */
  boolean save(RegistrationRuleChanged rule);

  /** Every rule kept, active or not (the reading decides what applies). */
  List<RegistrationRuleChanged> all();
}
