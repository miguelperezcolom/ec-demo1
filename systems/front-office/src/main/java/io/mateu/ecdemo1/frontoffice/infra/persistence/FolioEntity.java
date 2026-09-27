package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.folio.Folio;
import io.mateu.ecdemo1.frontoffice.domain.folio.FolioLine;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/** How a {@link Folio} is kept: the {@code folio} table and its lines. */
@Table("folio")
record FolioEntity(
    @Id String id,
    String stayId,
    BigDecimal preauthorized,
    @MappedCollection(idColumn = "folio_id", keyColumn = "idx") List<LineRow> lines) {

  @Table("folio_line")
  record LineRow(String concept, BigDecimal amount, boolean included, String includedLabel) {}

  static FolioEntity of(Folio f) {
    return new FolioEntity(f.id(), f.stayId(), f.preauthorized(), f.lines().stream()
        .map(l -> new LineRow(l.concept(), l.amount(), l.included(), l.includedLabel())).toList());
  }

  Folio toDomain() {
    return new Folio(id, stayId, preauthorized, lines == null ? List.of() : lines.stream()
        .map(l -> new FolioLine(l.concept(), l.amount(), l.included(), l.includedLabel())).toList());
  }
}
