package io.mateu.ecdemo1.frontoffice.infra.crs;

import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIns;
import io.mateu.ecdemo1.frontoffice.infra.outbox.CommandOutbox;
import io.mateu.ecdemo1.integration.model.command.ReportNoShow;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The hotel tells the chain a reservation's guests did not arrive (HLA F006). What that costs is the
 * CRS's to decide: it cancels the booking as a no-show with its fee, and the result comes back here —
 * the stay as a no-show, costing the fee — and to Opera. The report is an order, so it goes by Kafka:
 * written to the outbox with the desk's mark, in its transaction, and taken once by the CRS adapter.
 */
@Slf4j
@Service
public class NoShows {

  final String hotel;
  final WalkIns walkIns;
  final CommandOutbox outbox;

  public NoShows(WalkIns walkIns, CommandOutbox outbox, @Value("${frontoffice.hotel:MRU01}") String hotel) {
    this.walkIns = walkIns;
    this.outbox = outbox;
    this.hotel = hotel;
  }

  /** Reports the stay as a no-show, in the caller's transaction; what to tell the desk. */
  public String report(String stayId) {
    // A walk-in is a booking of the CRS under another name: the one the CRS gave it.
    var walkIn = walkIns.of(stayId).orElse(null);
    if (walkIn != null && walkIn.locator() == null) {
      return "Este walk-in aún no está en el CRS: el no show queda solo aquí.";
    }
    var locator = walkIn == null ? stayId : walkIn.locator();
    var commandId = "NS-" + UUID.randomUUID();
    var report = new ReportNoShow(commandId, hotel, locator, "front office " + hotel);
    outbox.append(CommandOutbox.NO_SHOW_REPORTS, report.key(), report);
    log.info("{}: reported to the CRS as a no-show ({})", stayId, commandId);
    return "Se comunica al CRS: si la reserva es suya, la cancela con su cargo de no show y la estancia lo mostrará.";
  }
}
