package io.mateu.ecdemo1.customerhistory.infra.in.ui.pages;

import io.mateu.uidl.annotations.Label;

/**
 * One stay in the grid, as the head office reads it: where, when, the room, and what it spent per kind.
 * The amounts without their currency, which has a column of its own: the grid is wide enough as it is.
 */
public record StayRow(String hotel, String llegada, String salida, int noches,
                      @Label("Habitación") String habitacion, String tipo, @Label("Régimen") String regimen,
                      String extras, @Label("Late check-out") String lateCheckOut, String consumos, String total,
                      String moneda, String cliente, String origen) {
}
