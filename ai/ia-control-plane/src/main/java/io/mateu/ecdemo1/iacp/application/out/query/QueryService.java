package io.mateu.ecdemo1.iacp.application.out.query;

import io.mateu.ecdemo1.uicommons.crud.CatalogueQueries;

import java.util.List;

/**
 * The read side of a catalogue, shaped for the three things that read it: the CRUD listings, which
 * need a page of rows and a total; the editors, which need one entry; and the metrics, which need
 * two counts. The first two are {@link CatalogueQueries}, what the CRUDs' base class reads.
 */
public interface QueryService<DtoType, RowType, IdType> extends CatalogueQueries<DtoType, RowType, IdType> {

    String getLabel(String id);

    List<DtoType> all();

    long count();

    long countEnabled();
}
