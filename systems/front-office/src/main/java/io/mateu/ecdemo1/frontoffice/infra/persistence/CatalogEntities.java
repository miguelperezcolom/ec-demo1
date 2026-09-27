package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.catalog.AddOnCatalogItem;
import io.mateu.ecdemo1.frontoffice.domain.catalog.ChargeCatalogItem;
import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** How the reference catalogs are kept: one row per charge, one per add-on. */
final class CatalogEntities {

  private CatalogEntities() {}

  @Table("charge_catalog_item")
  record ChargeRow(@Id String code, String name, BigDecimal price) {

    static ChargeRow of(ChargeCatalogItem i) {
      return new ChargeRow(i.code(), i.name(), i.price());
    }

    ChargeCatalogItem toDomain() {
      return new ChargeCatalogItem(code, name, price);
    }
  }

  @Table("add_on_catalog_item")
  record AddOnRow(@Id String id, String icon, String title, String description, BigDecimal price, String unit,
                  String includedLabel) {

    static AddOnRow of(AddOnCatalogItem i) {
      return new AddOnRow(i.id(), i.icon(), i.title(), i.description(), i.price(), i.unit(), i.includedLabel());
    }

    AddOnCatalogItem toDomain() {
      return new AddOnCatalogItem(id, icon, title, description, price, unit, includedLabel);
    }
  }
}
