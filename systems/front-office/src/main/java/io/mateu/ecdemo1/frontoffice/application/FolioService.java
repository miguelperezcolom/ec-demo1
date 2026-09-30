package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.catalog.ChargeCatalogItem;
import io.mateu.ecdemo1.frontoffice.domain.catalog.ChargeCatalogRepository;
import io.mateu.ecdemo1.frontoffice.domain.folio.ChargeKind;
import io.mateu.ecdemo1.frontoffice.domain.folio.Folio;
import io.mateu.ecdemo1.frontoffice.domain.folio.FolioLine;
import io.mateu.ecdemo1.frontoffice.domain.folio.FolioRepository;
import io.mateu.ecdemo1.frontoffice.infra.pms.ReceptionReports;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the desk charges to a stay's folio — a folio is opened for it if the stay had none — and what it
 * takes back. The PMS is the master of the folio: each charge, and each void, goes up to it in the same
 * transaction (an event in the outbox, {@link ReceptionReports}), so that the PMS's invoice covers what
 * the desk charged.
 */
@Service
public class FolioService {

  final FolioRepository folios;
  final ChargeCatalogRepository chargeCatalog;
  final ReceptionReports reception;
  final StayAudit audit;

  public FolioService(FolioRepository folios, ChargeCatalogRepository chargeCatalog, ReceptionReports reception,
                      StayAudit audit) {
    this.folios = folios;
    this.chargeCatalog = chargeCatalog;
    this.reception = reception;
    this.audit = audit;
  }

  /**
   * The guest leaves at 15:00 instead of 12:00, for {@link Folio#LATE_CHECK_OUT_FEE}. Charged once:
   * asked again, nothing more is charged.
   *
   * @return false if it was already contracted
   */
  @Transactional
  public boolean contractLateCheckOut(String stayId) {
    return contractLateCheckOut(stayId, null);
  }

  /** As {@link #contractLateCheckOut(String)}, saying who charges it. */
  @Transactional
  public boolean contractLateCheckOut(String stayId, String by) {
    return audit.run("Late check-out", stayId, by, StayAudit.params("fee", Folio.LATE_CHECK_OUT_FEE), () -> {
      var folio = folioOf(stayId);
      if (folio.lateCheckOutContracted()) {
        return false;
      }
      var saved = folios.save(folio.contractLateCheckOut());
      reception.chargePosted(stayId, saved.lines().getLast(), by);
      return true;
    }, contracted -> contracted ? "Contratado: salida a las 15:00" : "Ya estaba contratado");
  }

  /** Posts a catalog charge; empty if the catalog has no such code. */
  @Transactional
  public Optional<ChargeCatalogItem> postCharge(String stayId, String code) {
    return postCharge(stayId, code, null);
  }

  /** As {@link #postCharge(String, String)}, saying who charges it. */
  @Transactional
  public Optional<ChargeCatalogItem> postCharge(String stayId, String code, String by) {
    var item = chargeCatalog.findByCode(code);
    if (item.isEmpty()) {
      audit.failed("Charge posted", stayId, by, StayAudit.params("code", code), "No hay ningún cargo " + code + " en el catálogo");
      return item;
    }
    return audit.run("Charge posted", stayId, by, StayAudit.params("code", code, "concept", item.get().name(),
        "amount", item.get().price()), () -> {
          var i = item.get();
          var saved = folios.save(folioOf(stayId).post(FolioLine.charged(ChargeKind.CONSUMPTION, i.code(), i.name(), i.price())));
          reception.chargePosted(stayId, saved.lines().getLast(), by);
          return item;
        }, posted -> "Cargado al folio y enviado a Opera");
  }

  /**
   * Takes a charge of the desk back — a void, or a refund: the line stays on the folio and counts for
   * nothing, and the PMS reverses its posting. Empty if the stay's folio has no such line, or it is not
   * a charge of the desk (the accommodation is the PMS's); a line voided already is returned as it is.
   */
  @Transactional
  public Optional<FolioLine> voidCharge(String stayId, String lineId, String by) {
    var folio = folios.findByStayId(stayId).orElse(null);
    var line = folio == null ? null : folio.line(lineId).orElse(null);
    if (line == null || line.kind() == null || !line.kind().toThePms()) {
      audit.failed("Charge voided", stayId, by, StayAudit.params("line", lineId),
          "El folio no tiene ese cargo de recepción");
      return Optional.empty();
    }
    if (line.voided()) {
      return Optional.of(line);
    }
    return audit.run("Charge voided", stayId, by, StayAudit.params("line", lineId, "concept", line.concept(),
        "amount", line.amount()), () -> {
          folios.save(folio.voidLine(lineId));
          var voided = line.asVoided();
          reception.chargeVoided(stayId, voided, by);
          return Optional.of(voided);
        }, v -> "Anulado en el folio y revertido en Opera");
  }

  Folio folioOf(String stayId) {
    return folios.findByStayId(stayId).orElseGet(() -> Folio.emptyFor(stayId));
  }
}
