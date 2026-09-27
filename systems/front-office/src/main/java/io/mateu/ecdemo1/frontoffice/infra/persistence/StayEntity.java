package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.stay.Companion;
import io.mateu.ecdemo1.frontoffice.domain.stay.Incident;
import io.mateu.ecdemo1.frontoffice.domain.stay.IncidentStatus;
import io.mateu.ecdemo1.frontoffice.domain.stay.IncidentType;
import io.mateu.ecdemo1.frontoffice.domain.stay.SelectedAddOn;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/** How a {@link Stay} is kept: the {@code stay} table and its companions, incidents and add-ons. */
@Table("stay")
record StayEntity(
    @Id String id,
    String guestId,
    String roomNumber,
    String roomType,
    String board,
    LocalDate checkIn,
    LocalDate checkOut,
    int pax,
    String agency,
    BigDecimal total,
    StayStatus status,
    int wishesGranted,
    int wishesTotal,
    String vipNote,
    @MappedCollection(idColumn = "stay_id", keyColumn = "idx") List<CompanionRow> companions,
    @MappedCollection(idColumn = "stay_id", keyColumn = "idx") List<IncidentRow> incidents,
    @MappedCollection(idColumn = "stay_id") Set<AddOnRow> addOns) {

  @Table("stay_companion")
  record CompanionRow(String companionId, String name, String document, boolean documentVerified, String email,
                      String phone, String description) {}

  @Table("stay_incident")
  record IncidentRow(String code, IncidentType type, String icon, String title, String description,
                     IncidentStatus status, boolean complaint, LocalDateTime openedAt, LocalDateTime resolvedAt) {}

  @Table("stay_add_on")
  record AddOnRow(String addOnId) {}

  static StayEntity of(Stay s) {
    return new StayEntity(s.id(), s.guestId(), s.roomNumber(), s.roomType(), s.board(), s.checkIn(), s.checkOut(),
        s.pax(), s.agency(), s.total(), s.status(), s.wishesGranted(), s.wishesTotal(), s.vipNote(),
        s.companions().stream().map(c -> new CompanionRow(c.companionId(), c.name(), c.document(),
            c.documentVerified(), c.email(), c.phone(), c.description())).toList(),
        s.incidents().stream().map(i -> new IncidentRow(i.code(), i.type(), i.icon(), i.title(), i.description(),
            i.status(), i.complaint(), i.openedAt(), i.resolvedAt())).toList(),
        s.addOns().stream().map(a -> new AddOnRow(a.addOnId())).collect(Collectors.toSet()));
  }

  Stay toDomain() {
    return new Stay(id, guestId, roomNumber, roomType, board, checkIn, checkOut, pax, agency, total, status,
        wishesGranted, wishesTotal, vipNote,
        companions == null ? List.of() : companions.stream().map(c -> new Companion(c.companionId(), c.name(),
            c.document(), c.documentVerified(), c.email(), c.phone(), c.description())).toList(),
        incidents == null ? List.of() : incidents.stream().map(i -> new Incident(i.code(), i.type(), i.icon(),
            i.title(), i.description(), i.status(), i.complaint(), i.openedAt(), i.resolvedAt())).toList(),
        addOns == null ? Set.of() : addOns.stream().map(a -> new SelectedAddOn(a.addOnId()))
            .collect(Collectors.toSet()));
  }
}
