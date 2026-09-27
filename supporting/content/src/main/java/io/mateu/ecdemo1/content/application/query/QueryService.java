package io.mateu.ecdemo1.content.application.query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * The read side of a catalogue: a page of its rows, a label, and one entry. In the application's own
 * terms — a Spring Data page of rows — so that no screen's types reach it; the screens make their
 * listings out of the page.
 */
public interface QueryService<DtoType, RowType, IdType> {

    Page<RowType> findAll(String searchText, Object filters, Pageable pageable);

    String getLabel(String id);

    Optional<DtoType> getById(String id);

}
