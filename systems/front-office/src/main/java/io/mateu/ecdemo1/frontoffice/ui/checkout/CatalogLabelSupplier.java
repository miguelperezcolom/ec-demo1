package io.mateu.ecdemo1.frontoffice.ui.checkout;

import io.mateu.ecdemo1.frontoffice.domain.catalog.ChargeCatalogRepository;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.LookupLabelSupplier;
import org.springframework.stereotype.Service;

/** Resolves the display label of a picked charge-catalog item. */
@Service
public class CatalogLabelSupplier implements LookupLabelSupplier {

  private final ChargeCatalogRepository chargeCatalog;

  public CatalogLabelSupplier(ChargeCatalogRepository chargeCatalog) {
    this.chargeCatalog = chargeCatalog;
  }

  @Override
  public String label(String fieldName, Object id, HttpRequest httpRequest) {
    return chargeCatalog
        .findByCode(String.valueOf(id))
        .map(CatalogOptionsSupplier::label)
        .orElse(String.valueOf(id));
  }
}
