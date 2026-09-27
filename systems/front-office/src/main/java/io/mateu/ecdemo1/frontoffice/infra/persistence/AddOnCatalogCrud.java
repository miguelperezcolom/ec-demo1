package io.mateu.ecdemo1.frontoffice.infra.persistence;

import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC repository backing {@link H2AddOnCatalogRepository}. */
interface AddOnCatalogCrud extends ListCrudRepository<CatalogEntities.AddOnRow, String> {}
