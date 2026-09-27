package io.mateu.ecdemo1.booking.application.out.query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * The read side of an aggregate: a page of what a search matches, a label, and one of them. In the
 * application's own terms — a page of DTOs — so that no screen's types reach it; a screen makes its
 * rows out of the DTOs.
 */
public interface QueryService<DtoType, CriteriaType, IdType> {

    Page<DtoType> findAll(String searchText, CriteriaType criteria, Pageable pageable);

    String getLabel(IdType id);

    Optional<DtoType> getById(IdType id);

}
