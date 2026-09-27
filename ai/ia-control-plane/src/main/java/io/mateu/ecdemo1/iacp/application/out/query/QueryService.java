package io.mateu.ecdemo1.iacp.application.out.query;

import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * The read side of a catalogue, shaped for the three things that read it: the CRUD listings, which
 * need a page of rows and a total; the editors, which need one entry; and the metrics, which need
 * two counts.
 */
public interface QueryService<DtoType, RowType, IdType> {

    ListingData<RowType> findAll(String searchText, Object filters, Pageable pageable);

    String getLabel(String id);

    Optional<DtoType> getById(String id);

    List<DtoType> all();

    long count();

    long countEnabled();
}
