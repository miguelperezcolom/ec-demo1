package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.folio.Folio;
import io.mateu.ecdemo1.frontoffice.domain.folio.FolioRepository;
import java.util.Optional;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Repository;

/** H2 adapter of the {@link FolioRepository} port. */
@Repository
class H2FolioRepository implements FolioRepository {

  private final FolioCrud crud;
  private final JdbcAggregateTemplate template;

  H2FolioRepository(FolioCrud crud, JdbcAggregateTemplate template) {
    this.crud = crud;
    this.template = template;
  }

  @Override
  public Optional<Folio> findById(String id) {
    return RequestCache.get("folio:" + id, () -> crud.findById(id));
  }

  @Override
  public Optional<Folio> findByStayId(String stayId) {
    return RequestCache.get("folio-of-stay:" + stayId, () -> crud.findByStayId(stayId));
  }

  @Override
  public Folio save(Folio folio) {
    RequestCache.evict("folio:" + folio.id(), "folio-of-stay:" + folio.stayId());
    return crud.existsById(folio.id()) ? crud.save(folio) : template.insert(folio);
  }
}
