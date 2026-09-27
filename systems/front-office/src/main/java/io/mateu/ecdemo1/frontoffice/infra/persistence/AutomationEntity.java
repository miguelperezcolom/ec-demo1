package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.automation.Automation;
import io.mateu.ecdemo1.frontoffice.domain.automation.ConnectedSystem;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/** How an {@link Automation} is kept: the {@code automation} table and the systems it connects. */
@Table("automation")
record AutomationEntity(
    @Id String id,
    String name,
    int okCount,
    int warningCount,
    int errorCount,
    @MappedCollection(idColumn = "automation_id", keyColumn = "idx") List<SystemRow> systems) {

  @Table("automation_system")
  record SystemRow(String name) {}

  static AutomationEntity of(Automation a) {
    return new AutomationEntity(a.id(), a.name(), a.okCount(), a.warningCount(), a.errorCount(),
        a.systems().stream().map(s -> new SystemRow(s.name())).toList());
  }

  Automation toDomain() {
    return new Automation(id, name, okCount, warningCount, errorCount,
        systems == null ? List.of() : systems.stream().map(s -> new ConnectedSystem(s.name())).toList());
  }
}
