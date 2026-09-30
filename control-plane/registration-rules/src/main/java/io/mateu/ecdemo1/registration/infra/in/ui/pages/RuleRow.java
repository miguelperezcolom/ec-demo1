package io.mateu.ecdemo1.registration.infra.in.ui.pages;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.data.Status;

/** One rule in the listing: where, whom, what it requires, when, and why. */
public record RuleRow(String id, String nombre, @Label("Ámbito") String ambito, @Label("A quién") String aQuien,
                      String exige, @Label("Cuándo") String momentos, @Label("Base legal") String baseLegal,
                      String vigencia, Status estado) {
}
