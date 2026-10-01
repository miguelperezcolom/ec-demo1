package io.mateu.ecdemo1.registration.infra.in.ui.pages;

import io.mateu.uidl.annotations.Details;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.data.Status;

/**
 * One rule in the listing: where, whom, how much it requires, when. What it requires in full, what it
 * exempts and its legal basis open under the row — long texts that would push the listing sideways.
 */
public record RuleRow(String id, String nombre, @Label("Ámbito") String ambito, @Label("A quién") String aQuien,
                      String exige, @Label("Cuándo") String momentos, String vigencia, Status estado,
                      @Details String detalle) {
}
