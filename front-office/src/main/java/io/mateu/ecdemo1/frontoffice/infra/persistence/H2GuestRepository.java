package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Repository;

/** H2 adapter of the {@link GuestRepository} port. */
@Repository
class H2GuestRepository implements GuestRepository {

  private final GuestCrud crud;
  private final JdbcAggregateTemplate template;

  H2GuestRepository(GuestCrud crud, JdbcAggregateTemplate template) {
    this.crud = crud;
    this.template = template;
  }

  @Override
  public Optional<Guest> findById(String id) {
    return RequestCache.get("guest:" + id, () -> crud.findById(id));
  }

  @Override
  public List<Guest> findAll() {
    return crud.findAll();
  }

  @Override
  public Guest save(Guest guest) {
    RequestCache.evict("guest:" + guest.id());
    // Ids are application-assigned, so new aggregates must be inserted explicitly.
    return crud.existsById(guest.id()) ? crud.save(guest) : template.insert(guest);
  }
}
