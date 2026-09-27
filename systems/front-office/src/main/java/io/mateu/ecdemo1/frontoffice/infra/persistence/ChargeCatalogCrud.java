package io.mateu.ecdemo1.frontoffice.infra.persistence;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC repository backing {@link H2ChargeCatalogRepository}. */
interface ChargeCatalogCrud extends ListCrudRepository<CatalogEntities.ChargeRow, String> {
  List<CatalogEntities.ChargeRow> findByNameContainingIgnoreCase(String text);
}
