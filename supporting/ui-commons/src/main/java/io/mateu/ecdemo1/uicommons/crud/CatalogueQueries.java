package io.mateu.ecdemo1.uicommons.crud;

import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Pageable;

import java.util.Optional;

/**
 * What a {@link CatalogueCrud} reads: a page of rows, and one entry. A service's own query
 * interface extends it, so the same bean serves the CRUD and whatever else reads the catalogue.
 */
public interface CatalogueQueries<Dto, Row, Id> {

    ListingData<Row> findAll(String searchText, Object filters, Pageable pageable);

    Optional<Dto> getById(Id id);
}
