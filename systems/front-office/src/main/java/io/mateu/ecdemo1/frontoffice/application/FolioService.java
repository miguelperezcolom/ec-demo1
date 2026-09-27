package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.catalog.ChargeCatalogItem;
import io.mateu.ecdemo1.frontoffice.domain.catalog.ChargeCatalogRepository;
import io.mateu.ecdemo1.frontoffice.domain.folio.Folio;
import io.mateu.ecdemo1.frontoffice.domain.folio.FolioLine;
import io.mateu.ecdemo1.frontoffice.domain.folio.FolioRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What the desk charges to a stay's folio — a folio is opened for it if the stay had none. */
@Service
public class FolioService {

  final FolioRepository folios;
  final ChargeCatalogRepository chargeCatalog;

  public FolioService(FolioRepository folios, ChargeCatalogRepository chargeCatalog) {
    this.folios = folios;
    this.chargeCatalog = chargeCatalog;
  }

  /**
   * The guest leaves at 15:00 instead of 12:00, for {@link Folio#LATE_CHECK_OUT_FEE}. Charged once:
   * asked again, nothing more is charged.
   *
   * @return false if it was already contracted
   */
  @Transactional
  public boolean contractLateCheckOut(String stayId) {
    var folio = folioOf(stayId);
    if (folio.lateCheckOutContracted()) {
      return false;
    }
    folios.save(folio.contractLateCheckOut());
    return true;
  }

  /** Posts a catalog charge; empty if the catalog has no such code. */
  @Transactional
  public Optional<ChargeCatalogItem> postCharge(String stayId, String code) {
    var item = chargeCatalog.findByCode(code);
    item.ifPresent(i -> folios.save(folioOf(stayId).post(FolioLine.charge(i.name(), i.price()))));
    return item;
  }

  Folio folioOf(String stayId) {
    return folios.findByStayId(stayId).orElseGet(() -> Folio.emptyFor(stayId));
  }
}
