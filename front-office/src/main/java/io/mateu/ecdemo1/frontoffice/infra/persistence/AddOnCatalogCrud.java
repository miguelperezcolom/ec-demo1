package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.catalog.AddOnCatalogItem;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC repository backing {@link H2AddOnCatalogRepository}. */
interface AddOnCatalogCrud extends ListCrudRepository<AddOnCatalogItem, String> {}
