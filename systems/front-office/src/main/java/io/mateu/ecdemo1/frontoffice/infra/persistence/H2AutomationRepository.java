package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.automation.Automation;
import io.mateu.ecdemo1.frontoffice.domain.automation.AutomationRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Repository;

/** H2 adapter of the {@link AutomationRepository} port. */
@Repository
class H2AutomationRepository implements AutomationRepository {

  private final AutomationCrud crud;
  private final JdbcAggregateTemplate template;

  H2AutomationRepository(AutomationCrud crud, JdbcAggregateTemplate template) {
    this.crud = crud;
    this.template = template;
  }

  @Override
  public Optional<Automation> findById(String id) {
    return crud.findById(id).map(AutomationEntity::toDomain);
  }

  @Override
  public List<Automation> findAll() {
    return crud.findAll().stream().map(AutomationEntity::toDomain).toList();
  }

  @Override
  public Automation save(Automation automation) {
    var row = AutomationEntity.of(automation);
    return (crud.existsById(automation.id()) ? crud.save(row) : template.insert(row)).toDomain();
  }
}
