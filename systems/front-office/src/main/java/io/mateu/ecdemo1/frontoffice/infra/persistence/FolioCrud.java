package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.folio.Folio;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC repository backing {@link H2FolioRepository}. */
interface FolioCrud extends ListCrudRepository<Folio, String> {

  Optional<Folio> findByStayId(String stayId);
}
