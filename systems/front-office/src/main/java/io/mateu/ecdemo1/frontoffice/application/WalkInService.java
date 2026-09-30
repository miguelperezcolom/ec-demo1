package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIn;
import io.mateu.ecdemo1.frontoffice.infra.crs.WalkInDesk;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * A guest with no reservation at the desk. The price is the CRS's: it is asked for it first. The
 * confirmation opens the stay here — the guest, the stay and the walk-in in one transaction, so the
 * guest can check in at once — and then, outside it, sends the booking to the CRS at that price.
 */
@Service
public class WalkInService {

  final WalkInDesk desk;
  final StayAudit audit;

  public WalkInService(WalkInDesk desk, StayAudit audit) {
    this.desk = desk;
    this.audit = audit;
  }

  public WalkInDesk.Offer offer() {
    return desk.offer();
  }

  public WalkInDesk.Quote quote(WalkInDesk.Request request) {
    return desk.quote(request);
  }

  /** What the holder still lacks for the CRS to book it: nombre, apellidos, documento. */
  public static List<String> missing(WalkInDesk.Holder holder) {
    var missing = new ArrayList<String>();
    if (blank(holder.firstName())) missing.add("nombre");
    if (blank(holder.lastName())) missing.add("apellidos");
    if (blank(holder.documentNumber())) missing.add("documento");
    return missing;
  }

  /**
   * Opens the stay at the price the CRS quoted and asks the CRS for the booking. The stay is there
   * whatever the CRS answers: one that does not answer is asked again; a refusal is the desk's to see.
   */
  public WalkIn confirm(WalkInDesk.Request request, BigDecimal quotedTotal) {
    // audited once the stay exists — its id is the walk-in's — with what the CRS answered to the send
    var params = StayAudit.params("arrival", request.arrival(), "departure", request.departure(),
        "roomType", request.roomTypeCode(), "ratePlan", request.ratePlanCode(), "board", request.boardCode(),
        "adults", request.adults(), "total", quotedTotal);
    var missing = missing(request.holder());
    if (!missing.isEmpty()) {
      var why = "Falta del titular: " + String.join(", ", missing) + ".";
      audit.failed("Walk-in", null, null, params, why);
      throw new IllegalArgumentException(why);
    }
    WalkIn opened;
    try {
      opened = desk.open(request, new WalkInDesk.Quote(null, 0, quotedTotal));
    } catch (RuntimeException e) {
      audit.failed("Walk-in", null, null, params, e.getMessage());
      throw e;
    }
    var sent = desk.send(opened);
    audit.done("Walk-in", sent.stayId(), null, params, sent.label());
    return sent;
  }

  static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}
