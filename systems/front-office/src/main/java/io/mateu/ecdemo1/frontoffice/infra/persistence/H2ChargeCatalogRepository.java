package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.catalog.ChargeCatalogItem;
import io.mateu.ecdemo1.frontoffice.domain.catalog.ChargeCatalogRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Repository;

/** H2 adapter of the {@link ChargeCatalogRepository} port. */
@Repository
class H2ChargeCatalogRepository implements ChargeCatalogRepository {

  private final ChargeCatalogCrud crud;
  private final JdbcAggregateTemplate template;

  H2ChargeCatalogRepository(ChargeCatalogCrud crud, JdbcAggregateTemplate template) {
    this.crud = crud;
    this.template = template;
  }

  @Override
  public Optional<ChargeCatalogItem> findByCode(String code) {
    return crud.findById(code).map(CatalogEntities.ChargeRow::toDomain);
  }

  @Override
  public List<ChargeCatalogItem> findAll() {
    return crud.findAll().stream().map(CatalogEntities.ChargeRow::toDomain).toList();
  }

  @Override
  public List<ChargeCatalogItem> search(String text) {
    return text == null || text.isBlank()
        ? findAll()
        : crud.findByNameContainingIgnoreCase(text.trim()).stream().map(CatalogEntities.ChargeRow::toDomain).toList();
  }

  @Override
  public ChargeCatalogItem save(ChargeCatalogItem item) {
    var row = CatalogEntities.ChargeRow.of(item);
    return (crud.existsById(item.code()) ? crud.save(row) : template.insert(row)).toDomain();
  }
}
