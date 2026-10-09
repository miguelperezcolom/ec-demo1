package io.mateu.ecdemo1.loyalty.infra.in.ui.pages;

import io.mateu.uidl.annotations.Label;

/** What one stay earned one member: in the accruals listing, and under a member. */
public record AccrualRow(@Label("Socio") String memberNumber,
                         @Label("Estancia") String stayId,
                         @Label("Hotel") String hotelCode,
                         @Label("Noches") int nights,
                         @Label("Puntos") String points,
                         @Label("Fecha") String at) {
}
