package io.mateu.ecdemo1.loyalty.infra.in.ui.pages;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.data.Status;

/** One member in the listing. */
public record MemberRow(@Label("Número") String memberNumber,
                        @Label("Cliente") String customerCode,
                        @Label("Nivel") Status tier,
                        @Label("Puntos") String points,
                        @Label("Socio desde") String memberSince,
                        @Label("Actualizado") String updated) {
}
