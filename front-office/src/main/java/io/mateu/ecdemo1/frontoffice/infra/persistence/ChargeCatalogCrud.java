package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.catalog.ChargeCatalogItem;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC repository backing {@link H2ChargeCatalogRepository}. */
interface ChargeCatalogCrud extends ListCrudRepository<ChargeCatalogItem, String> {

  List<ChargeCatalogItem> findByNameContainingIgnoreCase(String text);
}
