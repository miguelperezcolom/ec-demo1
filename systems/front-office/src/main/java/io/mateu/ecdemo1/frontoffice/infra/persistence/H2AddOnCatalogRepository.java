package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.catalog.AddOnCatalogItem;
import io.mateu.ecdemo1.frontoffice.domain.catalog.AddOnCatalogRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Repository;

/** H2 adapter of the {@link AddOnCatalogRepository} port. */
@Repository
class H2AddOnCatalogRepository implements AddOnCatalogRepository {

  private final AddOnCatalogCrud crud;
  private final JdbcAggregateTemplate template;

  H2AddOnCatalogRepository(AddOnCatalogCrud crud, JdbcAggregateTemplate template) {
    this.crud = crud;
    this.template = template;
  }

  @Override
  public Optional<AddOnCatalogItem> findById(String id) {
    return crud.findById(id).map(CatalogEntities.AddOnRow::toDomain);
  }

  @Override
  public List<AddOnCatalogItem> findAll() {
    return crud.findAll().stream().map(CatalogEntities.AddOnRow::toDomain).toList();
  }

  @Override
  public AddOnCatalogItem save(AddOnCatalogItem item) {
    var row = CatalogEntities.AddOnRow.of(item);
    return (crud.existsById(item.id()) ? crud.save(row) : template.insert(row)).toDomain();
  }
}
