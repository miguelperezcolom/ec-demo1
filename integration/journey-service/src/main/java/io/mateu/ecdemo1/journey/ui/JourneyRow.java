package io.mateu.ecdemo1.journey.ui;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.data.Status;

/** A booking with traces lately; its id is the locator, which opens its journey. */
public record JourneyRow(@Label("Reserva") String id,
                         @Label("Hotel") String hotel,
                         @Label("Último cambio") String lastChange,
                         @Label("Qué fue") Status kind,
                         @Label("Cambios") int changes,
                         @Label("Duración de la traza") String duration) {
}
