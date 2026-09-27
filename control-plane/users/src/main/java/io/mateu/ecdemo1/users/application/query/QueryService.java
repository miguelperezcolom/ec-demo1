package io.mateu.ecdemo1.users.application.query;

import io.mateu.ecdemo1.uicommons.crud.CatalogueQueries;

/** The read side of a catalogue: its rows and one entry ({@link CatalogueQueries}, what the CRUDs read), and a label. */
public interface QueryService<DtoType, RowType, IdType> extends CatalogueQueries<DtoType, RowType, IdType> {

    String getLabel(IdType id);

}
